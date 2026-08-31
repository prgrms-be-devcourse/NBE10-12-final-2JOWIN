package com.twojo.auth.dto;

import java.util.UUID;

/**
 * 로그인 응답 (08 §A). refreshToken 필드를 두지 않는다 —
 * HttpOnly 쿠키(2jo_rt)로만 전달한다. 바디에 실으면 HttpOnly 가 무의미해진다 (검증 노트 #8).
 *
 * <p>role 은 화면 렌더링용 값이다. 실제 권한 판정은 access token 의 claim 으로 한다 (09 구현 위치).
 * <p>companyName 은 플랫폼 관리자 로그인에서 null 이다 (08 §A).
 */
public record LoginResponse(
        String accessToken,
        UUID memberId,
        String name,
        String role,
        String companyName) {}
