package com.twojo.auth.controller;

import com.twojo.auth.cookie.RefreshCookieFactory;
import com.twojo.auth.dto.LoginRequest;
import com.twojo.auth.dto.LoginResponse;
import com.twojo.auth.dto.RefreshTokenResponse;
import com.twojo.auth.service.AuthService;
import com.twojo.auth.service.LoginResult;
import com.twojo.auth.service.RotateResult;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 엔드포인트 (07 §A). refresh 원문은 바디를 거치지 않고 쿠키로만 오간다 (검증 노트 #8). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    /** 로그인 (AU-01·10) — access는 바디로, refresh는 Set-Cookie로 나간다. */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest servletRequest) {
        LoginResult result =
                authService.login(request, servletRequest.getRemoteAddr(), Instant.now());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookieFactory.issue(result.refreshToken(), request.rememberMe())
                                .toString())
                .body(result.response());
    }

    /**
     * 재발급 (AU-03) — 요청 바디가 없다. 쿠키가 곧 자격 증명이다 (08 §A).
     *
     * <p>required=false 로 받아 직접 검사한다. 그대로 두면 쿠키가 없을 때 Spring 이 400 을 내는데
     * 07 은 401 을 요구한다.
     *
     * <p>실패하면 죽은 쿠키를 지운다. 남겨두면 브라우저가 계속 붙여 보낸다 (08 §A).
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String rawToken,
            HttpServletResponse servletResponse) {

        if (rawToken == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);
        }

        // 한 번만 만든다 — 두 번 부르면 회전 판정과 쿠키 수명 계산의 기준 시각이 어긋난다
        Instant now = Instant.now();
        try {
            RotateResult result = authService.rotate(rawToken, now);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE,
                            refreshCookieFactory
                                    .reissue(result.refreshToken(), result.expiresAt(), now)
                                    .toString())
                    .body(result.response());
        } catch (BusinessException e) {
            // 예외는 GlobalExceptionHandler가 바디로 바꾸지만, 여기서 넣은 헤더는 살아남는다
            servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                    refreshCookieFactory.delete().toString());
            throw e;
        }
    }

    /**
     * 로그아웃 (AU-02) — 쿠키가 곧 자격 증명이라 access token을 요구하지 않는다.
     *
     * <p>요구하면 access 만료(15분) 뒤에는 로그아웃이 401이 되어, 가장 확실히 끊어야 할
     * 순간에 못 끊는다. 남의 세션은 그 쿠키 없이는 건드릴 수 없으므로 열어도 범위가 넓어지지
     * 않는다 (09 "인증 토큰은 본인 것만"). SameSite=Lax + Path 한정이라 CSRF 실익도 없다.
     *
     * <p>어떤 경로로 끝나든 204다 — 07 에러표에 로그아웃 행이 없다.
     * 쿠키는 항상 지운다. 남겨두면 브라우저가 죽은 값을 계속 붙여 보낸다 (07 쿠키 규약표).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false)
            String rawToken) {

        authService.logout(rawToken, Instant.now());

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.delete().toString())
                .build();
    }
}
