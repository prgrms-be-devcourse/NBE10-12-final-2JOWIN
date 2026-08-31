package com.twojo.auth.service;

import com.twojo.auth.dto.LoginRequest;
import com.twojo.auth.dto.LoginResponse;
import com.twojo.auth.dto.RefreshTokenResponse;
import com.twojo.auth.entity.ActorType;
import com.twojo.auth.entity.RefreshToken;
import com.twojo.auth.jwt.JwtProvider;
import com.twojo.auth.repository.RefreshTokenRepository;
import com.twojo.auth.token.SecureTokenFactory;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
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
     * 미가입·미설정·비활성 경로에서 대조할 더미 해시 — BCrypt 비용을 균등하게 맞추기 위한 것이다.
     *
     * <p>상수로 박지 않고 기동 때마다 만든다. matches()는 인코더 설정이 아니라 <b>해시 문자열에
     * 적힌 강도</b>로 검증하므로, 나중에 인코더 강도를 올리면 상수만 옛 비용으로 남아 차이가 되살아난다.
     */
    private String dummyPasswordHash;

    @PostConstruct
    void initDummyPasswordHash() {
        dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

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

        // 미가입·미설정·비활성이면 더미 해시와 대조한다 — 어느 경로로 가든 BCrypt를 정확히 한 번 돌려
        // 응답 시간이 계정의 존재나 상태를 드러내지 않게 한다 (SC-09)
        String hashToMatch = found
                .filter(MemberQuery.AuthCredential::active)
                .map(MemberQuery.AuthCredential::passwordHash)
                .orElse(dummyPasswordHash);
        boolean matched = passwordEncoder.matches(request.password(), hashToMatch);

        if (found.isEmpty()) {
            // 미가입 이메일도 기록한다 — 안 하면 잠기지 않는다는 사실로 계정 부재가 드러난다
            recordFailureAndThrow(request.email(), null, ipAddress, now);
        }

        MemberQuery.AuthCredential credential = found.get();
        if (!matched) {
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

    /**
     * 재발급 (회전) — 기존 행을 폐기하고 새 행을 만든다 (전이표 §9).
     *
     * <p>실패는 원인을 구별하지 않고 전부 REFRESH_TOKEN_NOT_ACTIVE다.
     * 특히 재사용을 감지했다는 사실을 응답으로 알려주지 않는다 (05 §9).
     *
     * <p><b>noRollbackFor</b> — 재사용 감지·비활성 구성원 경로는 세션을 폐기한 뒤 예외를 던진다.
     * 기본 규칙(RuntimeException이면 롤백)을 그대로 두면 그 폐기가 함께 사라져
     * 05 §9의 안전망이 코드상 존재하되 작동하지 않는다.
     * 예외를 던지는 네 자리 모두 커밋해도 안전하다 — 앞의 둘은 쓴 것이 없고, 뒤의 둘은 커밋이 목적이다.
     *
     * <p><b>여기에 새 쓰기를 추가할 때는</b> 그것이 실패 응답과 함께 커밋돼도 되는지 먼저 확인한다.
     *
     * @param rawToken 쿠키에서 꺼낸 원문
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public RotateResult rotate(String rawToken, Instant now) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(secureTokenFactory.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE));

        if (token.isRotated()) {
            // 한 번 쓴 토큰이 다시 왔다 — 정상 사용에서는 일어날 수 없다.
            // 훔쳐간 쪽과 진짜 사용자를 구별할 수 없으므로 이 구성원의 세션을 전부 끊는다
            revokeAllActive(token.getMemberId(), RefreshToken.RevokedReason.REUSE_DETECTED, now);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
        }
        if (!token.isUsableAt(now)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
        }

        // 폐기 누락 대비 안전망 — 비활성화 시점에 이미 끊겼어야 하지만 한 번 더 본다 (05 §9)
        if (!memberQuery.isActive(token.getMemberId())) {
            revokeAllActive(token.getMemberId(), RefreshToken.RevokedReason.MEMBER_DEACTIVATED, now);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
        }

        token.markUsed(now);
        token.revoke(RefreshToken.RevokedReason.ROTATED, now);

        String newRawToken = secureTokenFactory.generate();
        // 만료 시각을 상속한다 — 회전마다 갱신하면 Q-32의 12h·14d 상한이 무의미해진다.
        // 05 §7 열람 링크 재발송의 잔여 유효기간 상속과 같은 패턴이다
        Instant expiresAt = token.getExpiresAt();
        refreshTokenRepository.save(RefreshToken.issueForMember(
                token.getMemberId(), secureTokenFactory.hash(newRawToken), expiresAt));

        MemberQuery.AuthCredential credential = memberQuery.getCredential(token.getMemberId());
        String accessToken = jwtProvider.issue(
                credential.id(), credential.companyId(), credential.role(), now);

        return new RotateResult(new RefreshTokenResponse(accessToken), newRawToken, expiresAt);
    }

    /**
     * 활성 행을 전부 폐기한다 — 영속 엔티티라 더티 체킹으로 반영된다.
     *
     * <p>별도 빈 + REQUIRES_NEW로 분리하지 않는다. 분리하면 rotate()가 잡은 행 락을 쥔 채
     * 새 트랜잭션이 같은 행을 UPDATE하려 들어 무한 대기한다 (비활성 구성원 경로).
     * 락 대기 사이클이 아니라서 PostgreSQL 데드락 감지에도 걸리지 않는다.
     * 커밋 보장은 rotate()의 noRollbackFor가 맡는다.
     */
    private void revokeAllActive(UUID memberId, RefreshToken.RevokedReason reason, Instant now) {
        refreshTokenRepository
                .findByMemberIdAndStatus(memberId, RefreshToken.Status.ACTIVE)
                .forEach(t -> t.revoke(reason, now));
    }

    private void recordFailureAndThrow(String email, UUID memberId, String ipAddress, Instant now) {
        loginAttemptService.recordFailure(email, ActorType.MEMBER, memberId, ipAddress, now);
        throw new BusinessException(ErrorCode.LOGIN_FAILED);
    }
}
