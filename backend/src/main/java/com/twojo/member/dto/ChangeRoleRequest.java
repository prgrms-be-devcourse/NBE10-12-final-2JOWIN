package com.twojo.member.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 역할 변경 요청 (08 §A · MB-08).
 *
 * <p>문자열이라 @NotBlank는 "비어 있지 않다"까지만 본다. 값이 실제 역할인지는 서비스가
 * 확인한다 — 거기서 감싸지 않으면 enum 변환이 터져 500으로 나간다.
 */
public record ChangeRoleRequest(@NotBlank String role) {}
