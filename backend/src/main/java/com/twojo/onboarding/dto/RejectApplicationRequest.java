package com.twojo.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 반려 요청 (08 §A · ON-14).
 *
 * <p>사유가 필수다 — 반려 이력을 남기는 것이 Q-15가 반려 행을 보존하기로 한 이유다.
 * 길이는 {@code application.reject_reason VARCHAR(500)}과 맞춘다.
 */
public record RejectApplicationRequest(@NotBlank @Size(max = 500) String reason) {}
