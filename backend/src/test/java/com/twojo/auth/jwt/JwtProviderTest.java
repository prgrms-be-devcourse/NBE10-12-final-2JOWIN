package com.twojo.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.boundary.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** access token 왕복 (Q-32 15분 · 14 §3-4 HS256). */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JwtProviderTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private final JwtProvider jwtProvider =
            new JwtProvider("test-only-secret-key-at-least-32-bytes-long");

    @Test
    void 발급한_토큰을_파싱하면_담은_claim이_그대로_나온다() {
        String token = jwtProvider.issue(MEMBER_ID, COMPANY_ID, Role.COMPANY_ADMIN, Instant.now());

        Claims claims = jwtProvider.parse(token);

        // 필터가 AccessContext 를 만들 때 이 셋이 필요하다 (11 §1.4)
        assertThat(JwtProvider.subjectOf(claims)).isEqualTo(MEMBER_ID);
        assertThat(JwtProvider.companyIdOf(claims)).isEqualTo(COMPANY_ID);
        assertThat(JwtProvider.roleOf(claims)).isEqualTo(Role.COMPANY_ADMIN);
    }

    /** 08 §인증 · AU-08 — 관리자는 회사에 속하지 않고 역할이 하나뿐이라 담을 값이 없다 */
    @Test
    void 관리자_토큰에는_회사와_역할이_담기지_않는다() {
        // given/when — 관리자용으로 발급하면
        String token = jwtProvider.issueForPlatformAdmin(ADMIN_ID, Instant.now());

        // then — 주체는 담기고, 구성원용 claim 두 개는 아예 없다
        Claims claims = jwtProvider.parse(token);
        assertThat(JwtProvider.subjectOf(claims)).isEqualTo(ADMIN_ID);
        assertThat(claims.get("companyId")).isNull();
        assertThat(claims.get("role")).isNull();
    }

    @Test
    void 수명이_지난_토큰은_거부된다() {
        // 한 시간 전에 발급했으니 15분 수명은 45분 전에 끝났다.
        // parse 는 JJWT 내부 시계를 쓰므로 현재를 기준으로 상대 시각을 만든다
        String expired = jwtProvider.issue(MEMBER_ID, COMPANY_ID, Role.SALES_REP,
                Instant.now().minus(Duration.ofHours(1)));

        assertThatThrownBy(() -> jwtProvider.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
