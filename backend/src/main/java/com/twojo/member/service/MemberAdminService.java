package com.twojo.member.service;

import com.twojo.auth.SessionRevoker;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AuditActor;
import com.twojo.boundary.DealCommand;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.member.dto.ChangeRoleRequest;
import com.twojo.member.dto.DeactivateMemberRequest;
import com.twojo.member.dto.MemberOptionResponse;
import com.twojo.member.dto.MemberResponse;
import com.twojo.member.entity.Member;
import com.twojo.member.event.MemberDeactivated;
import com.twojo.member.repository.MemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구성원 관리 (MB-07~12 · 14) — 목록 · 담당자 선택지 · 역할 변경 · 비활성화 · 재활성화.
 *
 * <p>역할 위반은 403, 타사·미존재 구성원은 404다. 역할을 먼저 보는 순서에 이유가 있다 —
 * 스코프를 먼저 보면 403과 404의 차이가 "그 구성원이 우리 회사에 있는가"를 알려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAdminService {

    private final MemberRepository memberRepository;
    private final DealQuery dealQuery;
    private final DealCommand dealCommand;
    private final SessionRevoker sessionRevoker;
    private final ApplicationEventPublisher eventPublisher;

    /** 목록 (MB-07) — 비활성 구성원도 함께 나온다. 재활성화 대상을 찾는 화면이다. */
    public PageResponse<MemberResponse> list(AccessContext ctx, Pageable pageable) {
        requireAdmin(ctx);

        return PageResponse.from(
                memberRepository.findByCompanyId(ctx.companyId(), pageable).map(MemberResponse::of));
    }

    /**
     * 담당자 선택지 (DL-04) — 영업 담당자도 부른다.
     *
     * <p>페이징하지 않는다. 잘린 선택지는 배정할 수 없는 사람을 만드는데, 프론트는 잘렸다는
     * 사실을 알 방법이 없다.
     */
    public List<MemberOptionResponse> options(AccessContext ctx) {
        return memberRepository
                .findByCompanyIdAndStatusOrderByNameAsc(ctx.companyId(), Member.Status.ACTIVE)
                .stream()
                .map(MemberOptionResponse::of)
                .toList();
    }

    /**
     * 역할 변경 (MB-08) — 회사에 활성 관리자가 한 명도 없는 상태를 만들지 않는다 (MB-11).
     *
     * <p>관리자가 0명이 되면 되돌릴 사람이 없다. 역할 변경 자체가 기업 관리자 전용이다.
     */
    @Transactional
    public MemberResponse changeRole(AccessContext ctx, UUID memberId, ChangeRoleRequest request) {
        requireAdmin(ctx);

        Member member = findInScope(ctx, memberId);
        Role newRole = parseRole(request.role());

        if (newRole != Role.COMPANY_ADMIN && isLastActiveAdmin(ctx, member)) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
        member.changeRole(newRole);

        return MemberResponse.of(member);
    }

    /**
     * 비활성화 (MB-09·12·14) — 한 트랜잭션에서 담당 Deal을 넘기고, 상태를 바꾸고, 세션을 끊는다.
     *
     * <p>갈라지면 "구성원은 비활성인데 Deal은 그대로"나 그 반대가 남는다.
     *
     * <p>이관 대상은 <b>진행 중 담당 Deal이 있을 때만</b> 필요하다. 종결된 Deal은 옮기지 않으므로
     * 세는 집합과 옮기는 집합이 같다 — 다르면 "3건이라 이관 필수인데 실제로는 1건만 넘어가는" 어긋남이
     * 생긴다 (Q-48).
     *
     * <p>이미 비활성인 구성원을 다시 불러도 막지 않는다. 이 흐름은 몇 번을 돌아도 결과가 같고,
     * 05에 그 경우를 막는 전이가 없다. 다만 <b>이벤트는 상태가 실제로 바뀐 호출에서만</b> 나간다 —
     * 재호출까지 발행하면 아무 일도 없었던 호출이 감사 기록에 비활성화로 쌓인다.
     *
     * <p>세션 폐기와 이벤트가 같은 시각을 쓴다. {@code Instant.now()}를 각자 부르면 두 기록의
     * 시각이 갈려, 나중에 감사 로그와 세션 이력을 맞춰 볼 때 같은 사건인지 판단할 근거가 흐려진다.
     */
    @Transactional
    public MemberResponse deactivate(AccessContext ctx, UUID memberId,
                                     DeactivateMemberRequest request) {
        requireAdmin(ctx);

        Member member = findInScope(ctx, memberId);
        if (isLastActiveAdmin(ctx, member)) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
        boolean wasActive = member.isActive();
        UUID transferredTo = transferOpenDeals(ctx, member, request.transferToMemberId());

        member.deactivate();
        Instant occurredAt = Instant.now();
        sessionRevoker.revokeOnDeactivation(memberId, occurredAt);

        if (wasActive) {
            eventPublisher.publishEvent(new MemberDeactivated(
                    ctx.companyId(), memberId,
                    AuditActor.member(ctx.memberId()), occurredAt,
                    transferredTo));
        }
        return MemberResponse.of(member);
    }

    /** 재활성화 — 다시 로그인할 수 있게 된다. 담당하던 Deal은 이미 남에게 갔으므로 돌아오지 않는다. */
    @Transactional
    public MemberResponse reactivate(AccessContext ctx, UUID memberId) {
        requireAdmin(ctx);

        Member member = findInScope(ctx, memberId);
        member.reactivate();

        return MemberResponse.of(member);
    }

    /**
     * 담당 Deal 이관 — 넘길 것이 없으면 대상을 요구하지 않는다.
     *
     * <p>건수를 먼저 세는 이유는 07이 "0건이면 생략"을 허용하기 때문이다. 넘길 것이 있는데 대상이
     * 없으면 여기서 막고, 대상이 왔으면 받을 자격을 확인한 뒤 넘긴다.
     *
     * <p>넘긴 건수가 처음 센 건수와 다르면 그 사이 배정이 바뀐 것이다. 지금은 잠금이 없어 드물게
     * 생길 수 있고, 남은 Deal은 관리자가 담당자 변경으로 바로잡는다.
     *
     * @return 실제로 Deal을 넘겨받은 구성원. 한 건도 넘어가지 않았으면 {@code null}이다 —
     *     받은 인자를 그대로 돌려주지 않는 이유는, 넘길 Deal이 없어 이관을 건너뛴 경우에도
     *     요청에는 대상이 실려 올 수 있어서다. 호출자가 이 값을 감사 기록에 싣는다.
     */
    private UUID transferOpenDeals(AccessContext ctx, Member member, UUID transferToMemberId) {
        if (dealQuery.countOpenAssigned(ctx.companyId(), member.getId()) == 0) {
            return null;
        }
        if (transferToMemberId == null) {
            throw new BusinessException(ErrorCode.MEMBER_INACTIVE_TRANSFER_REQUIRED);
        }
        requireTransferTarget(ctx, member, transferToMemberId);

        List<UUID> moved =
                dealCommand.reassignOpenDeals(ctx.companyId(), member.getId(), transferToMemberId);

        return moved.isEmpty() ? null : transferToMemberId;
    }

    /**
     * 받을 사람 확인 — 같은 회사의 활성 구성원이어야 하고, 비활성화 대상 본인이면 안 된다.
     *
     * <p>본인을 따로 막는 이유는 이 시점에 본인이 아직 활성이라 다른 검사를 전부 통과하기 때문이다.
     * 통과하면 Deal이 곧 비활성이 될 사람에게 그대로 남는다.
     *
     * <p>셋 다 404다 — 우리 회사에 없는 id와 있는 id를 구별해 주면 그 차이로 구성원 목록을 캐낼 수 있다.
     */
    private void requireTransferTarget(AccessContext ctx, Member member, UUID transferToMemberId) {
        if (transferToMemberId.equals(member.getId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Member target = findInScope(ctx, transferToMemberId);
        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    /** 역할로 갈리는 행위의 실패는 404가 아니라 403이다 — 리소스 존재를 노출하지 않는다. */
    private void requireAdmin(AccessContext ctx) {
        if (ctx.role() != Role.COMPANY_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /** 회사 조건은 where에 있다. 조회한 뒤 비교하면 그 비교를 잊을 수 있는 자리가 생긴다. */
    private Member findInScope(AccessContext ctx, UUID memberId) {
        return memberRepository.findByIdAndCompanyId(memberId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 문자열을 역할로 바꾼다. 요청 필드가 String이라 오타가 검증을 통과해 여기까지 온다 —
     * 감싸지 않으면 변환 실패가 폴백 핸들러에 잡혀 500이 된다.
     *
     * <p>어느 필드가 틀렸는지 응답에 실어야 프론트가 고칠 자리를 안다 (07 부록).
     */
    private Role parseRole(String raw) {
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalidEnumField("role", Role.class);
        }
    }

    /** 비활성 관리자는 세지 않는다 — 강등해도 활성 관리자 수가 줄지 않아 막을 이유가 없다. */
    private boolean isLastActiveAdmin(AccessContext ctx, Member member) {
        if (member.getRole() != Role.COMPANY_ADMIN || !member.isActive()) {
            return false;
        }
        return memberRepository.countByCompanyIdAndRoleAndStatus(
                ctx.companyId(), Role.COMPANY_ADMIN, Member.Status.ACTIVE) == 1;
    }
}
