package com.twojo.member.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.member.dto.CreateInvitationRequest;
import com.twojo.member.dto.InvitationResponse;
import com.twojo.member.entity.Invitation;
import com.twojo.member.repository.InvitationRepository;
import com.twojo.member.repository.MemberRepository;
import com.twojo.member.token.InvitationTokenGenerator;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 발송·목록·재발송·취소 (MB-01·02·05·06·13) — 기업 관리자 전용.
 *
 * <p>재발송은 상태를 바꾸는 대신 <b>행을 하나 닫고 하나 여는</b> 패턴이다 (Q-31).
 * 같은 행의 토큰만 갈아끼우면 몇 번 보냈는지가 사라진다 — 행으로 남겨야 이력이 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final InvitationTokenGenerator tokenGenerator;

    /** 발송 (MB-01·02·13). */
    @Transactional
    public InvitationResponse create(AccessContext ctx, CreateInvitationRequest request) {
        requireAdmin(ctx);

        String email = normalize(request.email());
        Role role = parseRole(request.role());
        Instant now = Instant.now();

        requireNotMember(email);
        requirePendingSlotFree(ctx, email, now);

        return InvitationResponse.of(issue(ctx, email, role, now));
    }

    /** 목록 — status를 비우면 종결된 초대도 함께 나온다. */
    public PageResponse<InvitationResponse> list(AccessContext ctx, Invitation.Status status,
                                                 Pageable pageable) {
        requireAdmin(ctx);

        return PageResponse.from(
                (status == null
                        ? invitationRepository.findByCompanyId(ctx.companyId(), pageable)
                        : invitationRepository.findByCompanyIdAndStatus(ctx.companyId(), status, pageable))
                        .map(InvitationResponse::of));
    }

    /**
     * 재발송 (MB-06, Q-31) — 옛 행을 EXPIRED(RESENT)로 닫고 새 대기 행을 연다.
     *
     * <p>수신자·역할은 옛 행에서 그대로 가져온다. 바꾸려면 취소 후 새로 발송하는 것이
     * 맞다 — 재발송으로 역할이 바뀌면 받는 사람이 승낙한 조건과 달라진다.
     */
    @Transactional
    public InvitationResponse resend(AccessContext ctx, UUID invitationId) {
        requireAdmin(ctx);

        Invitation old = requirePending(findInScope(ctx, invitationId));
        Instant now = Instant.now();

        // 부분 유니크 인덱스는 대기 행에만 걸린다. INSERT가 UPDATE보다 먼저 나가므로
        // 여기서 밀어내지 않으면 대기 행이 잠깐 둘이 되어 인덱스가 터진다.
        old.expire(Invitation.ExpiredReason.RESENT, now);
        invitationRepository.flush();

        return InvitationResponse.of(issue(ctx, old.getEmail(), old.getRole(), now));
    }

    /** 취소 (MB-05). */
    @Transactional
    public InvitationResponse cancel(AccessContext ctx, UUID invitationId) {
        requireAdmin(ctx);

        Invitation invitation = requirePending(findInScope(ctx, invitationId));
        invitation.cancel(Instant.now());

        return InvitationResponse.of(invitation);
    }

    /**
     * 새 대기 행 발급 — 원문 토큰이 존재하는 유일한 자리다.
     *
     * <p>DB에는 해시만 남으므로 이 메서드가 끝나면 원문을 아는 곳이 사라진다.
     */
    private Invitation issue(AccessContext ctx, String email, Role role, Instant now) {
        String rawToken = tokenGenerator.generate();

        Invitation invitation = invitationRepository.save(Invitation.issue(
                ctx.companyId(), ctx.memberId(), email, role, tokenGenerator.hash(rawToken), now));

        // TODO(NT-01) 안내 메일 예약. MailCommand.TemplateType에 초대 상수가 없어 아직 못 부른다
        //  (D 요청 중, 이슈 #79). 그전까지 rawToken은 이 메서드를 벗어나지 않아 링크가 도달하지 않는다.
        return invitation;
    }

    private void requireAdmin(AccessContext ctx) {
        if (ctx.role() != Role.COMPANY_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private Invitation findInScope(AccessContext ctx, UUID invitationId) {
        return invitationRepository.findByIdAndCompanyId(invitationId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 종결된 초대는 되살아나지 않는다 — 재발송·취소 둘 다 대기 상태에서만 가능하다. */
    private Invitation requirePending(Invitation invitation) {
        if (!invitation.isPending()) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_PENDING);
        }
        return invitation;
    }

    /** 이미 계정이 있는 이메일은 초대하지 않는다 — 어느 회사든 마찬가지다 (MB-13, 이메일 전역 유일). */
    private void requireNotMember(String email) {
        if (memberRepository.findByEmailLower(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_MEMBER);
        }
    }

    /**
     * 대기 초대가 이 이메일을 점유하고 있는지 본다 — 회사·이메일당 대기 행은 하나다.
     *
     * <p>기한이 지난 행은 여기서 넘겨 자리를 비운다. 만료를 돌려주는 배치가 없어 이 자리가
     * 곧 만료 시점이다. 넘기지 않으면 죽은 초대가 그 이메일을 영구히 막는다.
     */
    private void requirePendingSlotFree(AccessContext ctx, String email, Instant now) {
        invitationRepository
                .findByCompanyIdAndEmailAndStatus(ctx.companyId(), email, Invitation.Status.PENDING)
                .ifPresent(pending -> {
                    if (!pending.isExpired(now)) {
                        throw new BusinessException(ErrorCode.EMAIL_ALREADY_MEMBER);
                    }
                    pending.expire(Invitation.ExpiredReason.TIME, now);
                    invitationRepository.flush();
                });
    }

    /** 저장 값을 소문자로 맞춘다 — 조회도 소문자로 하므로 둘이 어긋나면 대기 행을 못 찾는다. */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Role parseRole(String raw) {
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
