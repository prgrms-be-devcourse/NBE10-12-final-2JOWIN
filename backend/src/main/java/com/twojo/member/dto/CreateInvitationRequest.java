package com.twojo.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 초대 발송 요청 (08 §A · MB-01·02).
 *
 * <p>역할이 필수다 — 초대 시점에 정해지고 수락자가 고르지 않는다 (MB-02).
 * 문자열이라 값이 실제 역할인지는 서비스가 확인한다.
 */
public record CreateInvitationRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String role) {}
