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

    /** 07 쿠키 규약표 — 이름이 같으면 한 브라우저에서 나중 로그인이 앞 세션을 덮어쓴다 */
    @Test
    void 관리자_쿠키는_이름과_Path가_구성원과_다르다() {
        // given/when — 같은 원문으로 두 벌을 각각 구우면
        ResponseCookie 관리자 = factory.issue(ActorType.PLATFORM_ADMIN, RAW_TOKEN, false);
        ResponseCookie 구성원 = factory.issue(ActorType.MEMBER, RAW_TOKEN, false);

        // then — 관리자 쿠키는 자기 이름과 자기 Path 를 갖는다
        assertThat(관리자.getName()).isEqualTo("2jo_admin_rt");
        assertThat(관리자.getPath()).isEqualTo("/admin/api/v1/auth");

        // then — 두 벌이 겹치지 않아 한 브라우저에서 공존한다
        assertThat(관리자.getName()).isNotEqualTo(구성원.getName());
        assertThat(관리자.getPath()).isNotEqualTo(구성원.getPath());
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
