package com.twojo.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 정지 요청 (08 §A · ON-08).
 *
 * <p>길이는 {@code company.suspend_reason VARCHAR(500)}과 맞춘다.
 */
public record SuspendCompanyRequest(@NotBlank @Size(max = 500) String reason) {}
