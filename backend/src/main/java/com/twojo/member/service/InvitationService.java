package com.twojo.member.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.MailCommand;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
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
@Transactional(readOnly = true)
public class InvitationService {

    /** 메일 본문은 프론트를 거치지 않는 최종 표시물이라 서버가 KST로 바꿔 넣는다. */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** Locale.ROOT — 지역 설정에 따라 연도가 불교력으로 찍히는 것을 막는다. */
    private static final DateTimeFormatter EXPIRES_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private static final String MAIL_SUBJECT = "[2JO] 초대 안내";

    private static final String MAIL_BODY = """
            {companyName}에서 2JO 사용에 초대했습니다.

            아래 링크에서 이름과 비밀번호를 입력하면 계정이 만들어집니다.

            {link}

            이 링크는 {expiresAt} (KST)까지 유효합니다.
            """;

    private final InvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final InvitationTokenGenerator tokenGenerator;
    private final CompanyQuery companyQuery;
    private final MailCommand mailCommand;
    private final String invitationBaseUrl;

    /** @RequiredArgsConstructor를 쓰지 않는 이유는 baseUrl 하나 — @Value는 생성자 파라미터에 붙는다. */
    public InvitationService(InvitationRepository invitationRepository,
                             MemberRepository memberRepository,
                             InvitationTokenGenerator tokenGenerator,
                             CompanyQuery companyQuery,
                             MailCommand mailCommand,
                             @Value("${app.invitation.base-url}") String invitationBaseUrl) {
        this.invitationRepository = invitationRepository;
        this.memberRepository = memberRepository;
        this.tokenGenerator = tokenGenerator;
        this.companyQuery = companyQuery;
        this.mailCommand = mailCommand;
        this.invitationBaseUrl = invitationBaseUrl.endsWith("/")
                ? invitationBaseUrl.substring(0, invitationBaseUrl.length() - 1)
                : invitationBaseUrl;
    }

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
     * <p>DB에는 해시만 남는다. 원문은 여기서 조립하는 메일 본문으로만 나가고 반환값에는 담기지 않는다.
     */
    private Invitation issue(AccessContext ctx, String email, Role role, Instant now) {
        String rawToken = tokenGenerator.generate();

        Invitation invitation = invitationRepository.save(Invitation.issue(
                ctx.companyId(), ctx.memberId(), email, role, tokenGenerator.hash(rawToken), now));

        sendInvitationMail(invitation, rawToken);

        return invitation;
    }

    /**
     * 초대 안내 메일 예약 (NT-01).
     *
     * <p>발송 식별자는 방금 저장한 초대 행 id다. 재발송이 행을 새로 만들므로 메일 기록도 발송마다
     * 하나씩 생긴다 — 중복 발송을 막는 키에 이 값이 들어가서, 같은 값이 두 번 오면 두 번째가 막힌다.
     *
     * <p>수신 주소를 여기서 다시 다듬지 않는다. 발송은 create가 소문자로 맞춘 값을, 재발송은 그렇게
     * 저장된 값을 그대로 넘긴다 — 표기가 흔들리면 위 키가 달라져 중복 방어가 무력해진다.
     */
    private void sendInvitationMail(Invitation invitation, String rawToken) {
        mailCommand.schedule(
                MailCommand.TemplateType.INVITATION,
                invitation.getCompanyId(),
                invitation.getEmail(),
                invitation.getId(),
                MAIL_SUBJECT,
                renderBody(invitation, rawToken));
    }

    /**
     * 평문 최소 렌더 — 승인 통보·재설정 안내와 같은 수준이다. 템플릿 엔진도 확정 문안도 아직 없다.
     *
     * <p>formatted() 대신 replace를 쓴다. 포맷 문자열의 줄바꿈은 %n이어야 하는데 그 값은
     * 실행 환경을 따라가고, 메일 본문의 줄바꿈은 환경과 무관해야 한다.
     */
    private String renderBody(Invitation invitation, String rawToken) {
        return MAIL_BODY
                .replace("{companyName}", companyQuery.get(invitation.getCompanyId()).name())
                .replace("{link}", link(rawToken))
                .replace("{expiresAt}",
                        EXPIRES_AT_FORMAT.format(invitation.getExpiresAt().atZone(SEOUL)));
    }

    /** 수락 화면은 토큰을 경로로 받는다 — 재설정 링크가 쿼리로 붙는 것과 갈리는 지점이다. */
    private String link(String rawToken) {
        return invitationBaseUrl + "/" + rawToken;
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

    /**
     * 요청 필드가 String이라 오타가 @NotBlank를 통과해 여기까지 온다 (08 §A).
     * 어느 필드가 틀렸는지 응답에 실어야 프론트가 고칠 자리를 안다 (07 부록).
     */
    private Role parseRole(String raw) {
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalidEnumField("role", Role.class);
        }
    }
}
