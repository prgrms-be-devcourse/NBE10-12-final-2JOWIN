package com.twojo.member.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.member.dto.ChangeRoleRequest;
import com.twojo.member.dto.MemberOptionResponse;
import com.twojo.member.dto.MemberResponse;
import com.twojo.member.entity.Member;
import com.twojo.member.repository.MemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구성원 관리 (MB-07·08·11) — 목록 · 담당자 선택지 · 역할 변경.
 *
 * <p>역할 위반은 403, 타사·미존재 구성원은 404다. 역할을 먼저 보는 순서에 이유가 있다 —
 * 스코프를 먼저 보면 403과 404의 차이가 "그 구성원이 우리 회사에 있는가"를 알려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAdminService {

    private final MemberRepository memberRepository;

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
     */
    private Role parseRole(String raw) {
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
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
