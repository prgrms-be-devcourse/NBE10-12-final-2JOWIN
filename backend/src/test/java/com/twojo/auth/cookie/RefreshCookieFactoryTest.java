package com.twojo.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.auth.entity.ActorType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/** 쿠키 규약 (07 refresh 쿠키 규약표 · 회전 Max-Age 는 남은 기간). */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RefreshCookieFactoryTest {

    private static final String RAW_TOKEN = "kJ8mQ2xRf9vN";
    private static final Instant NOW = Instant.parse("2026-08-28T05:00:00Z");

    private final RefreshCookieFactory factory = new RefreshCookieFactory(true);

    @Test
    void 로그인_유지를_고르지_않으면_세션_쿠키가_된다() {
        ResponseCookie cookie = factory.issue(ActorType.MEMBER, RAW_TOKEN, false);

        // Max-Age 가 붙지 않아야 브라우저가 창을 닫을 때 버린다 (07)
        assertThat(cookie.getMaxAge()).isNegative();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void 로그인_유지를_고르면_Max_Age가_14일이다() {
        ResponseCookie cookie = factory.issue(ActorType.MEMBER, RAW_TOKEN, true);

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void 회전하면_남은_기간이_Max_Age가_된다() {
        // 12시간짜리로 발급된 뒤 15분이 지났다
        Instant expiresAt = NOW.plus(Duration.ofHours(12));

        ResponseCookie cookie =
                factory.reissue(ActorType.MEMBER, RAW_TOKEN, expiresAt,
                        NOW.plus(Duration.ofMinutes(15)));

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofHours(11).plusMinutes(45));
    }

    @Test
    void 삭제_쿠키는_값이_비어_있고_Max_Age가_0이다() {
        ResponseCookie cookie = factory.delete(ActorType.MEMBER);

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        // 발급 때와 Path 가 같아야 브라우저가 같은 쿠키로 보고 지운다
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    }
}
