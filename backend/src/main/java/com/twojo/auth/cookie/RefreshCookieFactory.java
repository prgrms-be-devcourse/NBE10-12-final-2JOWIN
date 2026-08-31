package com.twojo.auth.cookie;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * refresh 쿠키 발급·삭제 (07 refresh 쿠키 규약표).
 *
 * <p>refresh 원문이 응답 바디로 나가지 않게 하는 유일한 통로다 (검증 노트 #8).
 * 관리자 쿠키(2jo_admin_rt)는 Path가 달라 별도 사이클에서 다룬다 (AU-08).
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "2jo_rt";

    /** 좁을수록 좋다 — 일반 API 요청에는 아예 붙지 않는다 (07). */
    private static final String PATH = "/api/v1/auth";

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
    public ResponseCookie issue(String rawToken, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder builder = base(rawToken);
        return rememberMe ? builder.maxAge(REMEMBER_MAX_AGE).build() : builder.build();
    }

    /**
     * 회전 발급 — 남은 기간을 Max-Age로 쓴다.
     * refresh_token 행에 rememberMe가 없어 세션 쿠키였는지 알 수 없기 때문이다.
     * 노출 상한은 DB expires_at이 잡으므로 쿠키가 남아도 서버가 거부한다 (전이표 §9).
     */
    public ResponseCookie reissue(String rawToken, Instant expiresAt, Instant now) {
        Duration remaining = Duration.between(now, expiresAt);
        return base(rawToken)
                .maxAge(remaining.isNegative() ? Duration.ZERO : remaining)
                .build();
    }

    /**
     * 삭제 — 회전 실패와 로그아웃에서 쓴다 (07 · 08 §A).
     * 이름·Path·속성이 발급 때와 같아야 브라우저가 같은 쿠키로 보고 지운다.
     * Path를 빠뜨리면 기존 쿠키는 남고 빈 쿠키가 하나 더 생긴다.
     */
    public ResponseCookie delete() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(PATH);
    }
}
