package com.twojo.customer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 고객사 수정 요청 (CU-06).
 *
 * <p><b>온 값을 그대로 반영한다</b> — 08 §B에 "null 필드는 미변경" 주석이 없고 {@code name}이 필수라,
 * 수정 폼이 기존 값을 채워 전체를 보내는 것을 전제한다. 그래야 비고 같은 선택 항목을 비울 수 있다.
 * ({@code UpdateContactRequest}는 반대 — 보낸 필드만 바꾼다)
 */
public record UpdateCustomerRequest(
        @NotBlank String name,
        String industry,
        String size,
        String note) {}
