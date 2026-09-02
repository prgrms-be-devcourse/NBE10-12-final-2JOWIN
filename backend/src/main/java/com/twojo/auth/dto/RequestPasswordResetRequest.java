package com.twojo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 재설정 메일 요청 (08 §A · AU-05).
 *
 * <p>이 요청은 purpose=RESET(30분) 전용이다. INITIAL_SETUP(7일)은 가입 승인 시
 * 시스템이 발급하므로 이 엔드포인트를 타지 않는다 (Q-33·34).
 *
 * <p>@Email 은 형식만 본다. 실재 여부를 검사해 400을 내면 그 응답 자체가
 * 가입 여부를 알려주게 된다 (SC-09 인증 확장).
 */
public record RequestPasswordResetRequest(@NotBlank @Email String email) {}
