package com.twojo.auth.service;

import com.twojo.auth.dto.LoginRequest;
import com.twojo.auth.dto.LoginResponse;
import com.twojo.auth.entity.ActorType;
import com.twojo.auth.entity.RefreshToken;
import com.twojo.auth.jwt.JwtProvider;
import com.twojo.auth.repository.RefreshTokenRepository;
import com.twojo.auth.token.SecureTokenFactory;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 (AU-01·10). 회전·로그아웃은 별도 항목. */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Q-32 — 유지 미선택 12h / 선택 14d. 쿠키 Max-Age 규칙은 RefreshCookieFactory가 따로 갖는다. */
    private static final Duration REFRESH_TTL_DEFAULT = Duration.ofHours(12);
    private static final Duration REFRESH_TTL_REMEMBER = Duration.ofDays(14);

    private final MemberQuery memberQuery;
    private final CompanyQuery companyQuery;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureTokenFactory secureTokenFactory;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    /**
     * 로그인. 실패 사유를 응답으로 구별하지 않는다 — 미가입·미설정·비활성·불일치가 모두
     * LOGIN_FAILED다 (SC-09, 07 §A).
     *
     * @return 응답 바디와 refresh 원문. 쿠키로 굽는 것은 컨트롤러의 몫이다 (검증 노트 #8)
     */
    @Transactional
    public LoginResult login(LoginRequest request, String ipAddress, Instant now) {
        if (loginAttemptService.isLocked(request.email(), ActorType.MEMBER, now)) {
            // 비밀번호를 확인하지 않으므로 시도로 기록하지 않는다 — 기록하면 잠금이 무한 연장된다
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }

        Optional<MemberQuery.AuthCredential> found =
                memberQuery.findCredentialByEmail(request.email());

        if (found.isEmpty()) {
            // 미가입 이메일도 기록한다 — 안 하면 잠기지 않는다는 사실로 계정 부재가 드러난다
            recordFailureAndThrow(request.email(), null, ipAddress, now);
        }

        MemberQuery.AuthCredential credential = found.get();
        if (!credential.active()
                || credential.passwordHash() == null
                || !passwordEncoder.matches(request.password(), credential.passwordHash())) {
            recordFailureAndThrow(request.email(), credential.id(), ipAddress, now);
        }

        loginAttemptService.recordSuccess(
                request.email(), ActorType.MEMBER, credential.id(), ipAddress, now);

        String rawToken = secureTokenFactory.generate();
        Duration ttl = request.rememberMe() ? REFRESH_TTL_REMEMBER : REFRESH_TTL_DEFAULT;
        // 기존 세션은 폐기하지 않는다 — 다중 기기 허용 (Q-28), 새 행만 추가
        refreshTokenRepository.save(RefreshToken.issueForMember(
                credential.id(), secureTokenFactory.hash(rawToken), now.plus(ttl)));

        String accessToken = jwtProvider.issue(
                credential.id(), credential.companyId(), credential.role(), now);
        // 성공이 확정된 뒤에 조회한다 — 실패할 요청에서 이 쿼리가 돌 이유가 없다
        String companyName = companyQuery.get(credential.companyId()).name();

        return new LoginResult(
                new LoginResponse(accessToken, credential.id(), credential.name(),
                        credential.role().name(), companyName),
                rawToken);
    }

    private void recordFailureAndThrow(String email, UUID memberId, String ipAddress, Instant now) {
        loginAttemptService.recordFailure(email, ActorType.MEMBER, memberId, ipAddress, now);
        throw new BusinessException(ErrorCode.LOGIN_FAILED);
    }
}
