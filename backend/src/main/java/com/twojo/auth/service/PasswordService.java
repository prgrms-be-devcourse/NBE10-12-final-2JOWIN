package com.twojo.auth.service;

import com.twojo.auth.SessionRevoker;
import com.twojo.auth.dto.ChangePasswordRequest;
import com.twojo.auth.dto.ExecutePasswordResetRequest;
import com.twojo.auth.dto.RequestPasswordResetRequest;
import com.twojo.auth.entity.PasswordResetToken;
import com.twojo.auth.repository.PasswordResetTokenRepository;
import com.twojo.auth.token.SecureTokenFactory;
import com.twojo.boundary.MemberCommand;
import com.twojo.boundary.MemberQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경·재설정 (AU-04·05).
 *
 * <p>교체와 세션 폐기를 한 트랜잭션으로 묶는다 — 05 §9가 둘을 한 전이의 원인과 효과로
 * 규정하므로, 갈라지면 "비밀번호는 바뀌었는데 옛 세션이 살아 있는" 상태가 생긴다.
 *
 * <p>member 테이블은 MemberCommand로만 건드린다. auth가 member를 직접 참조하면
 * SessionRevoker(member -> auth)와 맞물려 모듈 순환이 된다 (11 §7.3).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordService {

    private final MemberQuery memberQuery;
    private final MemberCommand memberCommand;
    private final SessionRevoker sessionRevoker;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecureTokenFactory secureTokenFactory;

    /**
     * 현재 비밀번호를 확인하고 교체한 뒤, 그 구성원의 세션을 전부 끊는다 (AU-04 · 05 §9).
     *
     * <p>바꿀 대상은 요청이 아니라 access token에서 온다 — 09 "본인 것만".
     *
     * <p>같은 now를 두 곳에 넘긴다. password_changed_at이 "이 시각 이후 발급된 토큰만 유효"의
     * 기준이라(06), 폐기 시각과 어긋나면 나중에 판정할 수 없다.
     */
    public void change(UUID memberId, ChangePasswordRequest request, Instant now) {
        MemberQuery.AuthCredential credential = memberQuery.getCredential(memberId);

        // passwordHash가 NULL인 미설정 계정(Q-33)도 여기서 걸린다 — matches는 false를 돌려준다
        if (!passwordEncoder.matches(request.currentPassword(), credential.passwordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);   // 07 §A v1.6.5
        }

        memberCommand.changePassword(memberId, passwordEncoder.encode(request.newPassword()), now);
        sessionRevoker.revokeOnPasswordChange(memberId, now);
    }

    /**
     * 재설정 요청 (AU-05) — RESET 토큰(30분)을 하나 발급한다.
     *
     * <p><b>미가입 이메일도 응답이 같다</b> (SC-09 인증 확장). 인증 없이 누구나 부를 수 있는
     * 엔드포인트라, 응답이 갈리면 이메일 목록을 넣어 가입 여부를 훑을 수 있다.
     *
     * <p><b>메일 발송은 아직 없다.</b> 예약 통로(email_log)가 D 소유인데 계약이 없어
     * 이번 사이클은 발급까지만 한다 (#42). 계약이 생기면 rawToken을 링크에 넣어
     * 예약하는 호출이 아래 자리에 들어간다 — 원문을 반환하지 않는 것은 D의
     * ViewTokenCommand.issue와 같은 모양이다 (14 §7.3 토큰 로그 노출 금지).
     */
    public void requestReset(RequestPasswordResetRequest request, Instant now) {
        Optional<MemberQuery.AuthCredential> credential =
                memberQuery.findCredentialByEmail(request.email());

        if (credential.isEmpty()) {
            return;
        }
        UUID memberId = credential.get().id();

        // 활성 1개 유지 (05 §10) — 기존 행이 없으면(첫 요청) 아무 일도 하지 않는다
        passwordResetTokenRepository
                .findByMemberIdAndStatus(memberId, PasswordResetToken.Status.ACTIVE)
                .ifPresent(PasswordResetToken::expire);

        // 만료 UPDATE를 먼저 내보낸다. Hibernate는 INSERT를 UPDATE보다 앞에 내보내므로
        // 이 줄이 없으면 새 ACTIVE가 먼저 들어가 uk_password_reset_token_active에 걸린다
        passwordResetTokenRepository.flush();

        String rawToken = secureTokenFactory.generate();
        passwordResetTokenRepository.save(PasswordResetToken.issue(
                memberId, PasswordResetToken.Purpose.RESET, secureTokenFactory.hash(rawToken), now));

        // 메일 예약이 들어올 자리 — 링크 URL = baseUrl + rawToken
    }

    /**
     * 재설정 실행 (AU-05) — 토큰을 검증해 USED로 넘기고 비밀번호를 설정한다 (05 §10).
     *
     * <p>현재 비밀번호를 묻지 않는다 — 토큰 자체가 자격 증명이다. memberId도 요청이 아니라
     * 토큰 행에서 온다.
     *
     * <p>RESET·INITIAL_SETUP 공용이라 purpose를 보지 않는다 (Q-33). 수명 차이는 발급 시점에
     * expiresAt으로 이미 반영됐고 검증은 그 값으로 끝난다. INITIAL_SETUP이면 이 호출로
     * password_hash가 NULL에서 벗어난다.
     */
    public void executeReset(ExecutePasswordResetRequest request, Instant now) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(secureTokenFactory.hash(request.token()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_NOT_ACTIVE));

        // 못 쓰는 행이면 use()의 가드가 같은 예외를 던진다 — 없는 토큰과 구별해 알리지 않는다
        token.use(now);

        UUID memberId = token.getMemberId();
        memberCommand.changePassword(memberId, passwordEncoder.encode(request.newPassword()), now);
        sessionRevoker.revokeOnPasswordChange(memberId, now);
    }
}
