package com.twojo.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 고객사 등록 요청 (CU-01·02). 등록자는 서버가 AccessContext에서 채운다 — 요청 바디에 없다. */
public record CreateCustomerRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String industry,
        @Size(max = 50) String size,
        String note) {}
