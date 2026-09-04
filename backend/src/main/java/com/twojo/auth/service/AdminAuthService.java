package com.twojo.auth.service;

import com.twojo.auth.dto.LoginRequest;
import com.twojo.auth.dto.LoginResponse;
import com.twojo.auth.dto.RefreshTokenResponse;
import com.twojo.auth.entity.ActorType;
import com.twojo.auth.entity.RefreshToken;
import com.twojo.auth.jwt.JwtProvider;
import com.twojo.auth.repository.RefreshTokenRepository;
import com.twojo.auth.token.SecureTokenFactory;
import com.twojo.boundary.PlatformAdminQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 관리자 인증 (AU-08) — 로그인 · 회전 · 로그아웃.
 *
 * <p>구성원 인증과 뼈대가 같지만 별도 클래스로 둔다. 관리자는 회사에 속하지 않아
 * 정지 검사가 없고 세션도 다른 컬럼을 쓴다. 한 클래스에서 주체로 분기하면
 * 타이밍 균등화까지 두 갈래로 갈라진다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    /** Q-32 — 유지 미선택 12h / 선택 14d. 구성원과 같은 값이다. */
    private static final Duration REFRESH_TTL_DEFAULT = Duration.ofHours(12);
    private static final Duration REFRESH_TTL_REMEMBER = Duration.ofDays(14);

    private final PlatformAdminQuery platformAdminQuery;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureTokenFactory secureTokenFactory;
    private final JwtProvider jwtProvider;
    private final PasswordMatcher passwordMatcher;
    private final SessionRevokeService sessionRevokeService;

    /**
     * 관리자 로그인 (AU-08). 실패 사유를 응답으로 구별하지 않는다 —
     * 미가입·비활성·불일치가 모두 LOGIN_FAILED다.
     *
     * @return 응답 바디와 refresh 원문. 쿠키로 굽는 것은 컨트롤러의 몫이다
     */
    @Transactional
    public LoginResult login(LoginRequest request, String ipAddress, Instant now) {
        if (loginAttemptService.isLocked(request.email(), ActorType.PLATFORM_ADMIN, now)) {
            // 비밀번호를 확인하지 않으므로 시도로 기록하지 않는다 — 기록하면 잠금이 무한 연장된다
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }

        Optional<PlatformAdminQuery.AdminCredential> found =
                platformAdminQuery.findCredentialByEmail(request.email());

        // 미가입·비활성이면 null을 넘긴다 — 어느 경로로 가든 BCrypt를 정확히 한 번 돌려
        // 응답 시간이 계정의 존재나 상태를 드러내지 않게 한다 (SC-09)
        boolean matched = passwordMatcher.matches(request.password(), found
                .filter(PlatformAdminQuery.AdminCredential::active)
                .map(PlatformAdminQuery.AdminCredential::passwordHash)
                .orElse(null));

        if (found.isEmpty() || !matched) {
            // 미가입 이메일도 기록한다 — 안 하면 잠기지 않는다는 사실로 계정 부재가 드러난다.
            // 관리자에게는 구성원 id가 없어 이메일과 주체 구분만 남는다
            loginAttemptService.recordFailure(
                    request.email(), ActorType.PLATFORM_ADMIN, null, ipAddress, now);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        PlatformAdminQuery.AdminCredential credential = found.get();
        loginAttemptService.recordSuccess(
                request.email(), ActorType.PLATFORM_ADMIN, null, ipAddress, now);

        String rawToken = secureTokenFactory.generate();
        Duration ttl = request.rememberMe() ? REFRESH_TTL_REMEMBER : REFRESH_TTL_DEFAULT;
        // 기존 세션은 폐기하지 않는다 — 다중 기기 허용 (Q-28), 새 행만 추가
        refreshTokenRepository.save(RefreshToken.issueForPlatformAdmin(
                credential.id(), secureTokenFactory.hash(rawToken), now.plus(ttl)));

        String accessToken = jwtProvider.issueForPlatformAdmin(credential.id(), now);
        // 이름 컬럼이 없어 email을 표시 이름으로 쓴다. 소속 회사가 없어 companyName은 null이다
        return new LoginResult(
                new LoginResponse(accessToken, credential.id(), credential.email(),
                        ActorType.PLATFORM_ADMIN.name(), null),
                rawToken);
    }

    /**
     * 재발급 (회전) — 기존 행을 폐기하고 새 행을 만든다 (전이표 §9).
     *
     * <p>실패는 원인을 구별하지 않고 전부 REFRESH_TOKEN_NOT_ACTIVE다.
     * 특히 재사용을 감지했다는 사실을 응답으로 알려주지 않는다.
     *
     * <p><b>noRollbackFor</b> — 재사용 감지 경로는 세션을 폐기한 뒤 예외를 던진다.
     * 기본 규칙(RuntimeException이면 롤백)을 그대로 두면 그 폐기가 함께 사라져
     * 안전망이 코드상 존재하되 작동하지 않는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public RotateResult rotate(String rawToken, Instant now) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(secureTokenFactory.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE));

        // 구성원 행이면 여기서 끊는다 — 통과시키면 아래가 null인 관리자 id로 조회를 돌린다.
        // 재사용 검사보다 앞이어야 한다. 뒤로 밀면 회전된 구성원 행에서 null을 넘겨 폐기를 시도한다
        if (!token.isPlatformAdminSession()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
        }

        if (token.isRotated()) {
            // 한 번 쓴 토큰이 다시 왔다 — 훔쳐간 쪽과 진짜 사용자를 구별할 수 없어 둘 다 끊는다
            sessionRevokeService.revokeAllActiveForAdmin(
                    token.getPlatformAdminId(), RefreshToken.RevokedReason.REUSE_DETECTED, now);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
        }
        if (!token.isUsableAt(now)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
        }

        // 비활성 관리자는 재발급만 막고 남은 세션은 건드리지 않는다.
        // 관리자 계정을 비활성으로 바꾸는 경로가 아직 없어 폐기 사유를 새로 만들 근거가 없다
        if (!platformAdminQuery.isActive(token.getPlatformAdminId())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
        }

        token.markUsed(now);
        token.revoke(RefreshToken.RevokedReason.ROTATED, now);

        String newRawToken = secureTokenFactory.generate();
        // 만료 시각을 상속한다 — 회전마다 갱신하면 12h·14d 상한이 무의미해진다
        Instant expiresAt = token.getExpiresAt();
        refreshTokenRepository.save(RefreshToken.issueForPlatformAdmin(
                token.getPlatformAdminId(), secureTokenFactory.hash(newRawToken), expiresAt));

        String accessToken = jwtProvider.issueForPlatformAdmin(token.getPlatformAdminId(), now);

        return new RotateResult(new RefreshTokenResponse(accessToken), newRawToken, expiresAt);
    }

    /**
     * 관리자 로그아웃 (AU-02·08) — 쿠키로 제시된 그 세션 하나만 끊는다.
     *
     * <p>다중 기기를 허용하므로 다른 기기의 세션은 건드리지 않는다.
     * 실패 경로가 없다 — 쿠키가 없거나 이미 폐기된 토큰이어도 "세션 없음"은
     * 로그아웃의 목표 상태지 오류가 아니다.
     */
    @Transactional
    public void logout(String rawToken, Instant now) {
        if (rawToken == null) {
            return;
        }
        refreshTokenRepository
                .findByTokenHash(secureTokenFactory.hash(rawToken))
                .ifPresent(token -> token.revoke(RefreshToken.RevokedReason.LOGOUT, now));
    }
}
