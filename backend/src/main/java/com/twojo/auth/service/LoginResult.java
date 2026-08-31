package com.twojo.auth.service;

import com.twojo.auth.dto.LoginResponse;

/**
 * 로그인 결과 — 응답 바디와 refresh 원문을 함께 넘긴다.
 * LoginResponse에 refresh 필드를 둘 수 없으므로(검증 노트 #8) 서비스 내부 계약으로 분리했다.
 */
public record LoginResult(LoginResponse response, String refreshToken) {}
