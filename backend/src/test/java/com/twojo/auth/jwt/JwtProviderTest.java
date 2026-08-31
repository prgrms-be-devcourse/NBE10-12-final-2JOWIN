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

    private final JwtProvider jwtProvider =
            new JwtProvider("test-only-secret-key-at-least-32-bytes-long");

    @Test
    void 발급한_토큰을_파싱하면_담은_claim이_그대로_나온다() {
        String token = jwtProvider.issue(MEMBER_ID, COMPANY_ID, Role.COMPANY_ADMIN, Instant.now());

        Claims claims = jwtProvider.parse(token);

        // 필터가 AccessContext 를 만들 때 이 셋이 필요하다 (11 §1.4)
        assertThat(JwtProvider.memberIdOf(claims)).isEqualTo(MEMBER_ID);
        assertThat(JwtProvider.companyIdOf(claims)).isEqualTo(COMPANY_ID);
        assertThat(JwtProvider.roleOf(claims)).isEqualTo(Role.COMPANY_ADMIN);
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
