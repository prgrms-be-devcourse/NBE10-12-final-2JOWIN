package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.twojo.auth.dto.LoginRequest;
import com.twojo.auth.entity.RefreshToken;
import com.twojo.auth.jwt.JwtProvider;
import com.twojo.auth.repository.RefreshTokenRepository;
import com.twojo.auth.token.SecureTokenFactory;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.PlatformAdminQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 관리자 로그인 실패 통일 (SC-09) · 응답 두 필드 (08 §인증) · 주인 검사 (AU-08).
 * 구성원 쪽과 갈리는 지점만 본다 — 공통 규칙은 AuthServiceTest 가 이미 덮는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AdminAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
    private static final String EMAIL = "admin@2jo.io";
    private static final String PASSWORD = "test1234!";
    private static final String IP = "127.0.0.1";
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock private PlatformAdminQuery platformAdminQuery;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private MemberQuery memberQuery;

    private PasswordEncoder passwordEncoder;
    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        // 값이 정해져 있고 부수효과가 없는 것들은 실물을 쓴다 — 목으로 감싸면 더미 해시 대조가 검증되지 않는다
        passwordEncoder = new BCryptPasswordEncoder();
        adminAuthService = new AdminAuthService(platformAdminQuery, loginAttemptService,
                refreshTokenRepository, new SecureTokenFactory(),
                new JwtProvider("test-only-secret-key-at-least-32-bytes-long"),
                new PasswordMatcher(passwordEncoder),
                new SessionRevokeService(refreshTokenRepository, memberQuery));
    }

    // ── 로그인

    /** SC-09 · 07 §A — 구별되면 관리자 계정의 존재가 응답으로 드러난다 */
    @Test
    void 미가입_이메일과_비밀번호_불일치가_같은_응답을_낸다() {
        // given — 첫 시도는 없는 이메일, 둘째 시도는 있는 계정에 틀린 비밀번호
        given(platformAdminQuery.findCredentialByEmail(EMAIL))
                .willReturn(Optional.empty(), Optional.of(credential(true)));

        // when/then — 두 경로가 같은 코드로 끝난다
        assertThatThrownBy(() -> adminAuthService.login(request(PASSWORD), IP, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);

        assertThatThrownBy(() -> adminAuthService.login(request("틀린-비밀번호"), IP, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
    }

    /** 08 §인증 — platform_admin 에 이름 컬럼이 없고 관리자는 회사에 속하지 않는다 */
    @Test
    void 로그인_응답은_이름에_이메일을_회사명에_null을_담는다() {
        // given — 자격이 맞는 활성 관리자
        given(platformAdminQuery.findCredentialByEmail(EMAIL))
                .willReturn(Optional.of(credential(true)));

        // when — 올바른 비밀번호로 로그인하면
        LoginResult result = adminAuthService.login(request(PASSWORD), IP, NOW);

        // then — 08 §인증의 다섯 필드가 관리자 형태로 채워진다
        assertThat(result.response().memberId()).isEqualTo(ADMIN_ID);
        assertThat(result.response().name()).isEqualTo(EMAIL);
        assertThat(result.response().role()).isEqualTo("PLATFORM_ADMIN");
        assertThat(result.response().companyName()).isNull();
        assertThat(result.response().accessToken()).isNotBlank();
    }

    // ── 회전

    /** ON-11 · AU-08 — token_hash 는 표 전체에서 유일해 조회만으로는 어느 쪽 행인지 알 수 없다 */
    @Test
    void 구성원_refresh_토큰으로_관리자_재발급을_시도하면_거부된다() {
        // given — 구성원 세션 행이 관리자 경로로 제시됐다
        RefreshToken 구성원_세션 = RefreshToken.issueForMember(
                UUID.randomUUID(), "hash", NOW.plus(Duration.ofDays(14)));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(구성원_세션));

        // when/then — 통과시키면 구성원이 관리자 access token 을 받는다
        assertThatThrownBy(() -> adminAuthService.rotate("구성원-원문", NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);

        // then — 주인 검사가 앞에서 끊어 관리자 조회까지 가지 않는다.
        // 이 단정이 없으면 검사를 빼도 초록불이다 — isActive(null) 이 목에서 false 를 돌려
        // 같은 예외로 끝나기 때문이다
        verify(platformAdminQuery, never()).isActive(any());

        // then — 남의 종류 행은 손대지 않는다
        assertThat(구성원_세션.getStatus()).isEqualTo(RefreshToken.Status.ACTIVE);
    }

    /** AU-08 — 구성원과 갈리는 유일한 지점. 관리자에 맞는 폐기 사유가 없어 재발급만 막는다 */
    @Test
    void 비활성_관리자는_재발급받을_수_없고_남은_세션은_그대로다() {
        // given — 살아 있는 세션을 가진 관리자가 그사이 비활성이 됐다
        RefreshToken 활성_세션 = RefreshToken.issueForPlatformAdmin(
                ADMIN_ID, "hash", NOW.plus(Duration.ofDays(14)));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(활성_세션));
        given(platformAdminQuery.isActive(ADMIN_ID)).willReturn(false);

        // when/then — 재발급은 막힌다
        assertThatThrownBy(() -> adminAuthService.rotate("관리자-원문", NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);

        // then — 구성원 쪽과 달리 폐기하지 않는다. 05 에 그 전이가 없다
        assertThat(활성_세션.getStatus()).isEqualTo(RefreshToken.Status.ACTIVE);
    }

    private LoginRequest request(String password) {
        return new LoginRequest(EMAIL, password, true);
    }

    private PlatformAdminQuery.AdminCredential credential(boolean active) {
        return new PlatformAdminQuery.AdminCredential(
                ADMIN_ID, EMAIL, passwordEncoder.encode(PASSWORD), active);
    }
}
