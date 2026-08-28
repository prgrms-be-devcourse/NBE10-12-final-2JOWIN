package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.twojo.auth.dto.LoginRequest;
import com.twojo.auth.entity.RefreshToken;
import com.twojo.auth.jwt.JwtProvider;
import com.twojo.auth.repository.RefreshTokenRepository;
import com.twojo.auth.token.SecureTokenFactory;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로그인 실패 통일 (SC-09 · 07 §A) · 재사용 감지 (05 §9) · 만료 상속 (Q-32).
 * 셋 다 틀려도 정상 동작처럼 보여서 조용히 뚫리는 종류다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T05:00:00Z");
    private static final String EMAIL = "seoyeon@hanbit.co.kr";
    private static final String PASSWORD = "test1234!";
    private static final String IP = "127.0.0.1";
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @Mock private MemberQuery memberQuery;
    @Mock private CompanyQuery companyQuery;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    private PasswordEncoder passwordEncoder;
    private SecureTokenFactory secureTokenFactory;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // 값이 정해져 있고 부수효과가 없는 셋은 실물을 쓴다 — 목으로 감싸면 BCrypt 비교가 검증되지 않는다
        passwordEncoder = new BCryptPasswordEncoder();
        secureTokenFactory = new SecureTokenFactory();
        authService = new AuthService(memberQuery, companyQuery, loginAttemptService,
                refreshTokenRepository, secureTokenFactory,
                new JwtProvider("test-only-secret-key-at-least-32-bytes-long"), passwordEncoder);
    }

    // ── 로그인 성공

    @Test
    void 정상_로그인하면_세션이_발급되고_성공이_기록된다() {
        given(memberQuery.findCredentialByEmail(EMAIL))
                .willReturn(Optional.of(credential(true, passwordEncoder.encode(PASSWORD))));
        given(companyQuery.get(COMPANY_ID))
                .willReturn(new CompanyQuery.CompanySummary(COMPANY_ID, "한빛오피스", true));

        LoginResult result = authService.login(request(true), IP, NOW);

        // 08 §A 다섯 필드가 채워진다
        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().memberId()).isEqualTo(MEMBER_ID);
        assertThat(result.response().name()).isEqualTo("김서연");
        assertThat(result.response().role()).isEqualTo("COMPANY_ADMIN");
        assertThat(result.response().companyName()).isEqualTo("한빛오피스");

        // refresh 원문은 바디가 아니라 이 자리로만 나간다 (검증 노트 #8)
        assertThat(result.refreshToken()).isNotBlank();

        // DB 에는 원문이 아니라 해시가 저장된다 (14 §2-1)
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(secureTokenFactory.hash(result.refreshToken()));

        verify(loginAttemptService).recordSuccess(eq(EMAIL), any(), eq(MEMBER_ID), any(), any());
    }

    // ── 로그인 실패 네 갈래는 모두 같은 응답이다 (SC-09)

    @Test
    void 미가입_이메일이면_LOGIN_FAILED() {
        given(memberQuery.findCredentialByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request(false), IP, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
    }

    @Test
    void 비밀번호가_설정되지_않은_계정이면_LOGIN_FAILED() {
        // 가입 승인 직후 상태 — password_hash 가 NULL 이다 (Q-33)
        given(memberQuery.findCredentialByEmail(EMAIL))
                .willReturn(Optional.of(credential(true, null)));

        assertThatThrownBy(() -> authService.login(request(false), IP, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
    }

    @Test
    void 비활성_구성원이면_LOGIN_FAILED() {
        given(memberQuery.findCredentialByEmail(EMAIL))
                .willReturn(Optional.of(credential(false, passwordEncoder.encode(PASSWORD))));

        assertThatThrownBy(() -> authService.login(request(false), IP, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
    }

    @Test
    void 비밀번호가_틀리면_LOGIN_FAILED() {
        given(memberQuery.findCredentialByEmail(EMAIL))
                .willReturn(Optional.of(credential(true, passwordEncoder.encode("다른비밀번호"))));

        assertThatThrownBy(() -> authService.login(request(false), IP, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
    }

    @Test
    void 잠긴_상태면_비밀번호를_확인하지_않고_LOGIN_LOCKED() {
        given(loginAttemptService.isLocked(any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> authService.login(request(false), IP, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_LOCKED);

        // 기록하면 마지막 실패가 갱신되어 잠금이 무한 연장된다
        verify(loginAttemptService, never()).recordFailure(any(), any(), any(), any(), any());
    }

    // ── 회전

    @Test
    void 회전하면_기존_행이_ROTATED로_폐기된다() {
        RefreshToken active =
                RefreshToken.issueForMember(MEMBER_ID, "hash", NOW.plus(Duration.ofDays(14)));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(active));
        given(memberQuery.isActive(MEMBER_ID)).willReturn(true);
        given(memberQuery.getCredential(MEMBER_ID)).willReturn(credential(true, "hash"));

        RotateResult result = authService.rotate("아무-원문", NOW);

        // 한 번 쓴 토큰은 그 자리에서 죽는다 (전이표 §9)
        assertThat(active.getStatus()).isEqualTo(RefreshToken.Status.REVOKED);
        assertThat(active.getRevokedReason()).isEqualTo(RefreshToken.RevokedReason.ROTATED);
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    void 회전해도_만료_시각은_상속된다() {
        Instant expiresAt = NOW.plus(Duration.ofDays(14));
        RefreshToken active = RefreshToken.issueForMember(MEMBER_ID, "hash", expiresAt);
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(active));
        given(memberQuery.isActive(MEMBER_ID)).willReturn(true);
        given(memberQuery.getCredential(MEMBER_ID)).willReturn(credential(true, "hash"));

        RotateResult result = authService.rotate("아무-원문", NOW.plusSeconds(900));

        // 갱신하면 Q-32 의 14일 상한이 무의미해진다
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void 회전된_토큰을_다시_쓰면_활성_세션이_전부_폐기된다() {
        // 이미 한 번 쓰인 토큰과, 아직 살아 있는 다른 기기 세션
        RefreshToken used =
                RefreshToken.issueForMember(MEMBER_ID, "hash-used", NOW.plusSeconds(3600));
        used.revoke(RefreshToken.RevokedReason.ROTATED, NOW);
        RefreshToken otherDevice =
                RefreshToken.issueForMember(MEMBER_ID, "hash-other", NOW.plusSeconds(3600));

        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(used));
        given(refreshTokenRepository.findByMemberIdAndStatus(MEMBER_ID, RefreshToken.Status.ACTIVE))
                .willReturn(List.of(otherDevice));

        assertThatThrownBy(() -> authService.rotate("아무-원문", NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);

        // 훔친 쪽이 받아간 세션까지 함께 끊긴다
        assertThat(otherDevice.getStatus()).isEqualTo(RefreshToken.Status.REVOKED);
        assertThat(otherDevice.getRevokedReason())
                .isEqualTo(RefreshToken.RevokedReason.REUSE_DETECTED);
    }

    @Test
    void 없는_토큰으로_재발급하면_REFRESH_TOKEN_NOT_ACTIVE() {
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.rotate("위조된-원문", NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
    }

    @Test
    void 수명이_지난_토큰으로_재발급하면_REFRESH_TOKEN_NOT_ACTIVE() {
        // 만료됨 상태를 두지 않으므로 expires_at 비교로만 걸러진다 (전이표 §9)
        RefreshToken expired = RefreshToken.issueForMember(MEMBER_ID, "hash", NOW.minusSeconds(1));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.rotate("아무-원문", NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
    }

    @Test
    void 비활성_구성원의_토큰이면_남은_세션까지_폐기된다() {
        RefreshToken active = RefreshToken.issueForMember(MEMBER_ID, "hash", NOW.plusSeconds(3600));
        RefreshToken otherDevice =
                RefreshToken.issueForMember(MEMBER_ID, "hash-other", NOW.plusSeconds(3600));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(active));
        given(memberQuery.isActive(MEMBER_ID)).willReturn(false);
        given(refreshTokenRepository.findByMemberIdAndStatus(MEMBER_ID, RefreshToken.Status.ACTIVE))
                .willReturn(List.of(otherDevice));

        assertThatThrownBy(() -> authService.rotate("아무-원문", NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);

        // 거부만 하면 다른 기기 토큰이 살아남아 안전망 구실을 못 한다 (05 §9)
        assertThat(otherDevice.getRevokedReason())
                .isEqualTo(RefreshToken.RevokedReason.MEMBER_DEACTIVATED);
    }

    private LoginRequest request(boolean rememberMe) {
        return new LoginRequest(EMAIL, PASSWORD, rememberMe);
    }

    private MemberQuery.AuthCredential credential(boolean active, String passwordHash) {
        return new MemberQuery.AuthCredential(
                MEMBER_ID, COMPANY_ID, "김서연", Role.COMPANY_ADMIN, active, passwordHash);
    }
}
