package com.twojo.auth.service;

import com.twojo.auth.SessionRevoker;
import com.twojo.auth.dto.ChangePasswordRequest;
import com.twojo.auth.dto.ExecutePasswordResetRequest;
import com.twojo.auth.dto.RequestPasswordResetRequest;
import com.twojo.auth.entity.PasswordResetToken;
import com.twojo.auth.repository.PasswordResetTokenRepository;
import com.twojo.auth.token.SecureTokenFactory;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MemberCommand;
import com.twojo.boundary.MemberQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경(AU-04)·재설정(AU-05).
 *
 * <p>비밀번호 교체와 세션 폐기는 항상 한 트랜잭션이다. 갈라지면 비밀번호는 바뀌었는데
 * 옛 세션이 살아 있는 상태가 생긴다.
 *
 * <p>member 테이블은 MemberCommand로만 건드린다. 직접 참조하면 SessionRevoker가
 * 반대 방향(member -> auth)이라 모듈 순환이 되고 CI가 막는다.
 */
@Service
@Transactional
public class PasswordService {

    /** 메일 본문은 프론트를 거치지 않는 최종 표시물이라 서버가 KST로 바꿔 넣는다. */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** Locale.ROOT — 지역 설정에 따라 연도가 불교력으로 찍히는 것을 막는다. */
    private static final DateTimeFormatter EXPIRES_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private static final String MAIL_SUBJECT = "[2JO] 비밀번호 재설정 안내";

    private final MemberQuery memberQuery;
    private final MemberCommand memberCommand;
    private final SessionRevoker sessionRevoker;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecureTokenFactory secureTokenFactory;
    private final MailCommand mailCommand;
    private final String passwordResetBaseUrl;

    /** @RequiredArgsConstructor를 쓰지 않는 이유는 baseUrl 하나 — @Value는 생성자 파라미터에 붙는다. */
    public PasswordService(
            MemberQuery memberQuery,
            MemberCommand memberCommand,
            SessionRevoker sessionRevoker,
            PasswordEncoder passwordEncoder,
            PasswordResetTokenRepository passwordResetTokenRepository,
            SecureTokenFactory secureTokenFactory,
            MailCommand mailCommand,
            @Value("${app.password-reset.base-url}") String passwordResetBaseUrl) {
        this.memberQuery = memberQuery;
        this.memberCommand = memberCommand;
        this.sessionRevoker = sessionRevoker;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.secureTokenFactory = secureTokenFactory;
        this.mailCommand = mailCommand;
        this.passwordResetBaseUrl = passwordResetBaseUrl;
    }

    /**
     * 현재 비밀번호를 확인하고 교체한 뒤, 그 구성원의 세션을 전부 끊는다 (AU-04).
     *
     * <p>바꿀 대상은 요청 바디가 아니라 access token에서 온다 — 남의 비밀번호는 바꿀 수 없다.
     *
     * <p>같은 now를 교체와 폐기 양쪽에 넘긴다. password_changed_at이 "이 시각 이후 발급된
     * 토큰만 유효"의 기준이라, 두 시각이 어긋나면 나중에 판정할 수 없다.
     */
    public void change(UUID memberId, ChangePasswordRequest request, Instant now) {
        MemberQuery.AuthCredential credential = memberQuery.getCredential(memberId);

        // 비밀번호가 아직 없는 계정(가입 승인 직후)도 여기서 걸린다 — matches가 false를 돌려준다
        if (!passwordEncoder.matches(request.currentPassword(), credential.passwordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        memberCommand.changePassword(memberId, passwordEncoder.encode(request.newPassword()), now);
        sessionRevoker.revokeOnPasswordChange(memberId, now);
    }

    /**
     * 재설정 요청 (AU-05) — 30분짜리 토큰을 발급하고 링크가 담긴 안내 메일을 예약한다.
     *
     * <p>미가입 이메일도 결과가 같다. 인증 없이 누구나 부를 수 있는 엔드포인트라,
     * 응답이 갈리면 이메일 목록을 넣어 가입 여부를 훑을 수 있다.
     *
     * <p>구성원의 활성 여부는 보지 않는다 — 명세에 그 분기가 없어서 만들지 않았다.
     *
     * <p>원문 토큰은 메일 본문에만 실린다. DB에는 해시만 남고 이 메서드도 반환하지 않는다.
     */
    public void requestReset(RequestPasswordResetRequest request, Instant now) {
        Optional<MemberQuery.AuthCredential> credential =
                memberQuery.findCredentialByEmail(request.email());

        if (credential.isEmpty()) {
            return;
        }
        MemberQuery.AuthCredential found = credential.get();
        UUID memberId = found.id();

        // 구성원당 활성 토큰 1개 — 첫 요청이면 여기서 아무 일도 일어나지 않는다
        passwordResetTokenRepository
                .findByMemberIdAndStatus(memberId, PasswordResetToken.Status.ACTIVE)
                .ifPresent(PasswordResetToken::expire);

        // 만료 UPDATE를 먼저 내보낸다. Hibernate는 INSERT를 UPDATE보다 앞에 내보내므로
        // 이 줄이 없으면 새 ACTIVE가 먼저 들어가 활성 1개 제약에 걸린다
        passwordResetTokenRepository.flush();

        String rawToken = secureTokenFactory.generate();
        PasswordResetToken issued = passwordResetTokenRepository.save(PasswordResetToken.issue(
                memberId, PasswordResetToken.Purpose.RESET, secureTokenFactory.hash(rawToken), now));

        // refId는 방금 저장한 토큰 id다. 재요청마다 새 토큰이라 메일 기록도 매번 새로 생긴다 —
        // 이전 링크는 이미 만료됐고 새 링크가 나가야 하므로 덮어쓰지 않는 쪽이 맞다
        mailCommand.schedule(
                MailCommand.TemplateType.PASSWORD_RESET,
                found.companyId(),
                normalizeEmail(request.email()),
                issued.getId(),
                MAIL_SUBJECT,
                renderBody(rawToken, issued.getExpiresAt()));
    }

    /**
     * 재설정 실행 (AU-05) — 토큰을 검증해 사용됨으로 넘기고 비밀번호를 설정한다.
     *
     * <p>현재 비밀번호를 묻지 않는다. 토큰 자체가 자격 증명이고, 대상 구성원도 요청이 아니라
     * 토큰 행에서 온다.
     *
     * <p>재설정과 가입 후 최초 설정이 이 메서드를 공유한다. purpose를 보지 않는 것은
     * 수명 차이가 발급 시점에 expiresAt으로 이미 반영됐기 때문이다.
     */
    public void executeReset(ExecutePasswordResetRequest request, Instant now) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(secureTokenFactory.hash(request.token()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_NOT_ACTIVE));

        // 못 쓰는 토큰이면 use()가 같은 예외를 던진다 — 없는 토큰과 구별해 알리지 않는다
        token.use(now);

        UUID memberId = token.getMemberId();
        memberCommand.changePassword(memberId, passwordEncoder.encode(request.newPassword()), now);
        sessionRevoker.revokeOnPasswordChange(memberId, now);
    }

    /**
     * 메일 중복 발송을 막는 키에 이 값이 들어간다 — 같은 사람이 다른 표기로 오면 다른 사람이 된다.
     *
     * <p>Locale.ROOT가 필요한 이유는 인자 없는 toLowerCase()가 JVM 기본 로케일을 쓰기 때문이다.
     * 터키어 로케일에서는 'I'가 점 없는 'ı'로 바뀐다.
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 평문 최소 렌더. 템플릿 엔진도 다듬은 문안도 아직 없다 — 확정된 문안이 없어서다. */
    private String renderBody(String rawToken, Instant expiresAt) {
        String link = passwordResetBaseUrl + "?token=" + rawToken;
        String expiresAtText = EXPIRES_AT_FORMAT.format(expiresAt.atZone(SEOUL));
        return """
                비밀번호를 재설정하려면 아래 링크를 여세요.

                %s

                이 링크는 %s (KST)까지 유효합니다.
                요청한 적이 없다면 이 메일을 무시하세요.
                """.formatted(link, expiresAtText);
    }
}
