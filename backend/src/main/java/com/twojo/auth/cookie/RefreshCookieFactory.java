package com.twojo.auth.cookie;

import com.twojo.auth.entity.ActorType;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * refresh 쿠키 발급·삭제 (07 refresh 쿠키 규약표).
 *
 * <p>refresh 원문이 응답 바디로 나가지 않게 하는 유일한 통로다 (검증 노트 #8).
 *
 * <p>구성원과 관리자가 한 벌씩 갖는다. 이름이 같으면 한 브라우저에서 나중에 로그인한 쪽이
 * 앞의 세션을 덮어쓴다. 나머지 속성은 두 벌이 같아 이름과 Path만 actor로 고른다.
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "2jo_rt";
    public static final String ADMIN_COOKIE_NAME = "2jo_admin_rt";

    /** 좁을수록 좋다 — 일반 API 요청에는 아예 붙지 않는다 (07). */
    private static final String PATH = "/api/v1/auth";
    private static final String ADMIN_PATH = "/admin/api/v1/auth";

    /** 서브도메인 분리 배포라 Lax로 충분하다 (14 §1.4). 교차 오리진이 되면 None + Origin 검증. */
    private static final String SAME_SITE = "Lax";

    /** Q-32 — rememberMe 선택 시 14일. AuthService의 REFRESH_TTL_REMEMBER와 같은 값이다. */
    private static final Duration REMEMBER_MAX_AGE = Duration.ofDays(14);

    /** local은 http라 Secure를 켜면 브라우저가 쿠키를 저장하지 않는다 — Swagger 검증이 막힌다. */
    private final boolean secure;

    public RefreshCookieFactory(@Value("${app.cookie.secure}") boolean secure) {
        this.secure = secure;
    }

    /**
     * 로그인 발급 — rememberMe가 세션 쿠키와 영속 쿠키를 가른다 (07).
     * 세션 쿠키는 Max-Age를 아예 붙이지 않는다 — 0을 주면 즉시 삭제라 정반대 의미다.
     */
    public ResponseCookie issue(ActorType actor, String rawToken, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder builder = base(actor, rawToken);
        return rememberMe ? builder.maxAge(REMEMBER_MAX_AGE).build() : builder.build();
    }

    /**
     * 회전 발급 — 남은 기간을 Max-Age로 쓴다.
     * refresh_token 행에 rememberMe가 없어 세션 쿠키였는지 알 수 없기 때문이다.
     * 노출 상한은 DB expires_at이 잡으므로 쿠키가 남아도 서버가 거부한다 (전이표 §9).
     */
    public ResponseCookie reissue(ActorType actor, String rawToken, Instant expiresAt,
                                  Instant now) {
        Duration remaining = Duration.between(now, expiresAt);
        return base(actor, rawToken)
                .maxAge(remaining.isNegative() ? Duration.ZERO : remaining)
                .build();
    }

    /**
     * 삭제 — 회전 실패와 로그아웃에서 쓴다 (07 · 08 §A).
     * 이름·Path·속성이 발급 때와 같아야 브라우저가 같은 쿠키로 보고 지운다.
     * Path를 빠뜨리면 기존 쿠키는 남고 빈 쿠키가 하나 더 생긴다.
     */
    public ResponseCookie delete(ActorType actor) {
        return base(actor, "").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(ActorType actor, String value) {
        Spec spec = specOf(actor);
        return ResponseCookie.from(spec.name(), value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(spec.path());
    }

    /**
     * 두 벌이 다른 것은 이 둘뿐이다.
     *
     * <p>default를 두지 않는다 — 주체가 하나 늘면 여기서 컴파일이 깨져,
     * 쿠키를 정하지 않은 채로 지나가는 경로가 생기지 않는다.
     */
    private static Spec specOf(ActorType actor) {
        return switch (actor) {
            case MEMBER -> new Spec(COOKIE_NAME, PATH);
            case PLATFORM_ADMIN -> new Spec(ADMIN_COOKIE_NAME, ADMIN_PATH);
        };
    }

    /** 이름과 Path를 한 덩어리로 옮기기 위한 것 — 밖으로 나가지 않는다. */
    private record Spec(String name, String path) {}
}
