package com.twojo.auth.service;

import com.twojo.auth.dto.RefreshTokenResponse;
import java.time.Instant;

/**
 * 회전 결과 — 응답 바디와 새 refresh 원문, 그리고 상속된 만료 시각.
 *
 * <p>expiresAt을 함께 넘기는 이유는 쿠키 Max-Age를 컨트롤러가 계산해야 하기 때문이다.
 * 회전은 rememberMe를 알 수 없다 — refresh_token 행에 그 값이 없다 (항목 9에서 다룬다).
 */
public record RotateResult(RefreshTokenResponse response, String refreshToken, Instant expiresAt) {}
