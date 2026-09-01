package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
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
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
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
                new JwtProvider("test-only-secret-key-at-least-32-bytes-long"), passwordEncoder,
                // 목으로 감싸면 폐기 케이스가 스텁된 리포지토리에 닿지 않아 검증이 사라진다
                new SessionRevokeService(refreshTokenRepository, memberQuery));
    }

    // ── 로그인 성공

    @Test
    void 정상_로그인하면_세션이_발급되고_성공이_기록된다() {
        given(memberQuery.findCredentialByEmail(EMAIL))
                .willReturn(Optional.of(credential(true, passwordEncoder.encode(PASSWORD))));
        given(companyQuery.get(COMPANY_ID))
                .willReturn(new CompanyQuery.CompanySummary(
                        COMPANY_ID, "한빛오피스", "123-45-67890", true));

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

    @Nested
    class 정지된_회사의_구성원은 {

        /** 07 §A LOGIN_FAILED 행 · ON-09 — 정지 시 refresh 일괄 폐기가 재로그인으로 무효화되면 안 된다 */
        @Test
        void 로그인할_수_없다() {
            // given — 김서연은 활성 구성원이고 비밀번호도 맞지만, 한빛오피스가 정지됐다
            given(memberQuery.findCredentialByEmail(EMAIL))
                    .willReturn(Optional.of(credential(true, passwordEncoder.encode(PASSWORD))));
            given(companyQuery.get(COMPANY_ID))
                    .willReturn(new CompanyQuery.CompanySummary(
                            COMPANY_ID, "한빛오피스", "123-45-67890", false));

            // when — 올바른 비밀번호로 로그인을 시도하면
            // then — 자격 증명이 틀렸을 때와 똑같은 응답이 나간다 (SC-09)
            assertThatThrownBy(() -> authService.login(request(false), IP, NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);

            // 세션도 만들어지지 않는다
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // ── 회전

    @Test
    void 회전하면_기존_행이_ROTATED로_폐기된다() {
        RefreshToken active =
                RefreshToken.issueForMember(MEMBER_ID, "hash", NOW.plus(Duration.ofDays(14)));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(active));
        given(memberQuery.isActive(MEMBER_ID)).willReturn(true);
        given(memberQuery.getCredential(MEMBER_ID)).willReturn(credential(true, "hash"));
        given(companyQuery.get(COMPANY_ID)).willReturn(활성_회사());

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
        given(companyQuery.get(COMPANY_ID)).willReturn(활성_회사());

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

    // ── 로그아웃

    /** 05 §9 로그아웃 행 · Q-28 — 다중 기기를 허용하므로 끊는 것은 제시된 그 행 하나다 */
    @Test
    void 로그아웃해도_다른_기기_세션은_유지된다() {
        // given — 김서연이 노트북과 휴대폰 두 대에서 로그인해 있다
        RefreshToken 노트북 =
                RefreshToken.issueForMember(MEMBER_ID, "hash-laptop", NOW.plusSeconds(3600));
        RefreshToken 휴대폰 =
                RefreshToken.issueForMember(MEMBER_ID, "hash-phone", NOW.plusSeconds(3600));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(노트북));

        // 전 행 폐기로 잘못 구현하면 이 목록을 타고 휴대폰까지 끊긴다 — 그래야 아래 단정이 의미를 갖는다.
        // 올바른 구현은 이 스텁을 쓰지 않으므로 lenient 로 둔다
        lenient().when(refreshTokenRepository
                        .findByMemberIdAndStatus(MEMBER_ID, RefreshToken.Status.ACTIVE))
                .thenReturn(List.of(노트북, 휴대폰));

        // when — 노트북 쿠키로 로그아웃하면
        authService.logout("노트북-원문", NOW);

        // then — 휴대폰 세션은 손대지 않는다
        assertThat(휴대폰.getStatus()).isEqualTo(RefreshToken.Status.ACTIVE);
    }

    /** 05 §9 — 사유가 틀리면 감사 기록이 거짓말이 된다. 응답이 같아 눈으로는 안 보인다 */
    @Test
    void 로그아웃하면_그_행이_LOGOUT으로_폐기된다() {
        // given — 살아 있는 세션 하나가 쿠키로 제시된다
        RefreshToken 노트북 =
                RefreshToken.issueForMember(MEMBER_ID, "hash-laptop", NOW.plusSeconds(3600));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(노트북));

        // when — 로그아웃하면
        authService.logout("노트북-원문", NOW);

        // then — 그 행이 LOGOUT 사유로 폐기된다 (전이표 §9 로그아웃 행)
        assertThat(노트북.getStatus()).isEqualTo(RefreshToken.Status.REVOKED);
        assertThat(노트북.getRevokedReason()).isEqualTo(RefreshToken.RevokedReason.LOGOUT);
    }

    /** D2 · 07 에러표에 로그아웃 행이 없다 — 세션 없음은 목표 상태지 실패가 아니다 */
    @Test
    void 쿠키가_없으면_아무_것도_조회하지_않고_끝난다() {
        // when — 브라우저가 2jo_rt 를 안 보냈다 (이미 로그아웃했거나 쿠키가 만료됐다)
        authService.logout(null, NOW);

        // then — 예외도 조회도 없다. 여기서는 '아무 일도 일어나지 않는 것' 자체가 명세다
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    /** 05 §9 · RefreshToken#revoke — 최초 폐기 사유가 감사 근거다 */
    @Test
    void 이미_폐기된_토큰으로_로그아웃해도_최초_폐기_사유가_유지된다() {
        // given — 회전으로 이미 끊긴 행이 낡은 쿠키에 실려 다시 온다
        RefreshToken 회전됨 =
                RefreshToken.issueForMember(MEMBER_ID, "hash", NOW.plusSeconds(3600));
        회전됨.revoke(RefreshToken.RevokedReason.ROTATED, NOW);
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(회전됨));

        // when — 그 쿠키로 로그아웃하면
        authService.logout("낡은-원문", NOW.plusSeconds(60));

        // then — LOGOUT 이 덮어썼다면 침해 흔적이 지워진다
        assertThat(회전됨.getRevokedReason()).isEqualTo(RefreshToken.RevokedReason.ROTATED);
    }

    private LoginRequest request(boolean rememberMe) {
        return new LoginRequest(EMAIL, PASSWORD, rememberMe);
    }

    private CompanyQuery.CompanySummary 활성_회사() {
        return new CompanyQuery.CompanySummary(COMPANY_ID, "한빛오피스", "123-45-67890", true);
    }

    private MemberQuery.AuthCredential credential(boolean active, String passwordHash) {
        return new MemberQuery.AuthCredential(
                MEMBER_ID, COMPANY_ID, "김서연", Role.COMPANY_ADMIN, active, passwordHash);
    }
}
