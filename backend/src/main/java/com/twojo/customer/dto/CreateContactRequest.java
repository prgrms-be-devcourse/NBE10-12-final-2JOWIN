package com.twojo.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 고객사 담당자 등록 요청 (CU-09·10).
 *
 * <p>대표 여부는 여기서 정하지 않는다 — 별도 엔드포인트
 * {@code POST .../contacts/{cid}/set-primary}가 담당한다 (CU-11).
 */
public record CreateContactRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String title,
        @Size(max = 30) String phone,
        @NotBlank @Email @Size(max = 255) String email) {}
