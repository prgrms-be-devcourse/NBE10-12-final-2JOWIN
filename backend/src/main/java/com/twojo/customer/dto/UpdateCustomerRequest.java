package com.twojo.customer.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 고객사 수정 요청 (CU-06) — <b>PATCH: null 필드는 미변경</b> (08 §B).
 *
 * <p>업종·규모·비고는 빈 문자열로 비운다. 이름은 NOT NULL이라 비울 수 없다.
 */
public record UpdateCustomerRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String name,
        String industry,
        String size,
        String note) {}
