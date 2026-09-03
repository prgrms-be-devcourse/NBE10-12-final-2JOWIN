package com.twojo.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

/**
 * 고객사 담당자 수정 요청.
 *
 * <p><b>PATCH: null 필드는 미변경</b> (08 §B 주석 — v1.6 보강) — 필드 하나만 골라 보내는 화면을
 * 전제하므로, 안 보낸 필드는 건드리지 않는다.
 * (고객사·상품·활동·할 일도 같은 규약이다)
 *
 * <p>{@code name}·{@code email}은 NOT NULL 컬럼이라 {@code @Pattern}으로 공백을 막는다.
 * {@code @NotBlank}는 null까지 거절해 부분 수정을 막고, {@code @Email}만으로는 빈 문자열이
 * 통과한다. {@code title}·{@code phone}은 nullable이라 빈 문자열로 비울 수 있다.
 *
 * <p>대표 지정은 이 요청이 아니라 {@code POST .../contacts/{cid}/set-primary}다 (CU-11).
 * 지정 시 기존 대표가 자동 해제되며, <b>대표 해제만 하는 동작은 없다</b> — 대표 0명을 막기 위해서다.
 */
public record UpdateContactRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String name,
        String title,
        String phone,
        @Email @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String email) {}
