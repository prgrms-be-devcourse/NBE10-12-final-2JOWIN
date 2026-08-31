package com.twojo.auth.dto;

/**
 * 재발급 응답 (08 §A). 요청 record 는 없다 —
 * RefreshTokenRequest 는 v1.6.4 에서 폐기됐고 쿠키가 곧 자격 증명이다.
 *
 * <p>사용자 정보는 싣지 않는다 — 새로고침 복구는 /api/v1/me 가 담당한다 (12 §6.3).
 */
public record RefreshTokenResponse(String accessToken) {}
