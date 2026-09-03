package com.twojo.customer.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 고객사 수정 요청 (CU-06).
 *
 * <p><b>PATCH: null 필드는 미변경</b> (08 §B 주석) — 보낸 필드만 바꾼다.
 * 이름만 고치는 요청도, 기존 값을 채워 전체를 보내는 폼도 둘 다 유효하다.
 *
 * <p>{@code name}에 {@code @NotBlank} 대신 {@code @Pattern}을 쓴다 — Bean Validation은
 * {@code @Pattern}에서 null을 검사하지 않으므로 "안 보내는 건 되고, 보냈으면 공백은 안 된다"가
 * 그대로 표현된다. {@code @NotBlank}는 null까지 거절해 부분 수정을 막는다.
 *
 * <p>선택 항목({@code industry}·{@code size}·{@code note})은 빈 문자열로 비운다 —
 * null은 "안 보냈다"는 뜻이라 지우기와 구별된다.
 */
public record UpdateCustomerRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String name,
        String industry,
        String size,
        String note) {}
