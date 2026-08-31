package com.twojo.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;

import com.twojo.auth.jwt.JwtProvider;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 인증 필터의 명세 — 언제 인증되고 언제 안 되는가.
 *
 * <p>실패해도 <b>예외를 던지지 않는</b> 성질이 핵심이다. 필터는 DispatcherServlet 밖이라
 * 던지면 401이 아니라 500이 된다 (09 구현 위치).
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JwtAuthenticationFilterTest {

    private static final UUID MEMBER_ID = UUID.fromString("1e000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    private static final JwtProvider JWT =
            new JwtProvider("test-only-secret-key-at-least-32-bytes-long");
    private static final JwtProvider 다른_서버의_키 =
            new JwtProvider("another-secret-key-at-least-32-bytes-long-too");

    @Mock private MemberQuery memberQuery;
    @Mock private CompanyQuery companyQuery;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(JWT, memberQuery, companyQuery);
    }

    @AfterEach
    void 보관함을_비운다() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class 인증된_요청은 {

        /** 11 §1.4 · SC-02·05 — 뒤집히면 영업 담당자가 회사 전체 데이터를 본다 */
        @ParameterizedTest
        @CsvSource({"COMPANY_ADMIN, COMPANY_ALL", "SALES_REP, OWNED_ONLY"})
        void 역할에_맞는_조회_범위를_갖는다(Role role, AccessScope 기대_범위) {
            // given — 활성 구성원이고, 소속 회사도 정상이다
            given(memberQuery.getCredential(MEMBER_ID)).willReturn(자격(role, true));
            given(companyQuery.isSuspended(COMPANY_ID)).willReturn(false);

            // when — 유효한 access token 을 달고 요청하면
            Authentication 결과 = 필터를_통과시킨다("Bearer " + 유효한_토큰(role));

            // then — 토큰이 아니라 DB 의 현재 값으로 컨텍스트가 채워진다
            AccessContext ctx = (AccessContext) 결과.getPrincipal();
            assertThat(ctx.memberId()).isEqualTo(MEMBER_ID);
            assertThat(ctx.companyId()).isEqualTo(COMPANY_ID);
            assertThat(ctx.role()).isEqualTo(role);
            assertThat(ctx.scope()).isEqualTo(기대_범위);
        }
    }

    @Nested
    class 정지된_회사의_구성원은 {

        /** 09 구현 위치(인증 필터 층) · ON-09 — access token 15분 노출 창을 닫는 자리다 */
        @Test
        void 가진_access_token_으로_API_를_호출할_수_없다() {
            // given — 구성원 자신은 활성이지만 한빛오피스가 정지됐다
            given(memberQuery.getCredential(MEMBER_ID))
                    .willReturn(자격(Role.COMPANY_ADMIN, true));
            given(companyQuery.isSuspended(COMPANY_ID)).willReturn(true);

            // when — 정지 전에 발급받은 멀쩡한 토큰으로 요청하면
            // then — 인증되지 않는다. 이후 체인이 401 을 낸다
            assertThat(필터를_통과시킨다("Bearer " + 유효한_토큰(Role.COMPANY_ADMIN))).isNull();
        }
    }

    @Nested
    class 비활성_구성원은 {

        /** 09 구현 위치 · MB-10 — 비활성화 시점에 세션을 끊지만, 남은 access token 은 여기서 막는다 */
        @Test
        void 가진_access_token_으로_API_를_호출할_수_없다() {
            // given — 구성원이 비활성화됐다. 회사는 보지도 않는다 (단축 평가)
            given(memberQuery.getCredential(MEMBER_ID))
                    .willReturn(자격(Role.SALES_REP, false));

            assertThat(필터를_통과시킨다("Bearer " + 유효한_토큰(Role.SALES_REP))).isNull();
        }
    }

    @Nested
    class 잘못된_access_token_요청은 {

        /** D1 설계 · 09 구현 위치 — 하나라도 예외를 던지면 401 이 아니라 500 이 된다 */
        @ParameterizedTest
        @MethodSource("com.twojo.auth.filter.JwtAuthenticationFilterTest#잘못된_인증_헤더")
        void 예외_없이_미인증으로_남는다(String 헤더) {
            assertThatNoException().isThrownBy(() -> 필터를_통과시킨다(헤더));
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        /** getCredential 은 행이 없으면 BusinessException 을 던진다 — 필터가 안 잡으면 500 이다 */
        @Test
        void 구성원_행이_사라졌으면_미인증으로_남는다() {
            given(memberQuery.getCredential(MEMBER_ID))
                    .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            assertThat(필터를_통과시킨다("Bearer " + 유효한_토큰(Role.SALES_REP))).isNull();
        }
    }

    static Stream<Arguments> 잘못된_인증_헤더() {
        Instant 한참_전 = Instant.now().minus(Duration.ofHours(1));
        return Stream.of(
                arguments(Named.of("헤더 자체가 없다", null)),
                arguments(Named.of("Bearer 형식이 아니다", "Basic dXNlcjpwYXNz")),
                arguments(Named.of("다른 키로 서명됐다",
                        "Bearer " + 다른_서버의_키.issue(
                                MEMBER_ID, COMPANY_ID, Role.SALES_REP, Instant.now()))),
                arguments(Named.of("수명이 지났다",
                        "Bearer " + JWT.issue(MEMBER_ID, COMPANY_ID, Role.SALES_REP, 한참_전))));
    }

    /** 필터를 한 번 통과시키고 보관함에 남은 인증 정보를 돌려준다. 미인증이면 null 이다. */
    private Authentication 필터를_통과시킨다(String 인증_헤더) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        if (인증_헤더 != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, 인증_헤더);
        }
        MockFilterChain chain = new MockFilterChain();
        try {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        } catch (Exception e) {
            throw new AssertionError("필터는 어떤 경우에도 예외를 밖으로 내보내지 않는다", e);
        }
        // 성공이든 실패든 다음 필터로 넘어간다 — 여기서 멈추면 응답이 비어 나간다
        assertThat(chain.getRequest()).isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static String 유효한_토큰(Role role) {
        return JWT.issue(MEMBER_ID, COMPANY_ID, role, Instant.now());
    }

    private static MemberQuery.AuthCredential 자격(Role role, boolean active) {
        return new MemberQuery.AuthCredential(
                MEMBER_ID, COMPANY_ID, "김서연", role, active, "$2a$10$dummy");
    }
}
