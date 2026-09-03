package com.twojo.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

/**
 * 고객사 담당자 수정 요청 — <b>PATCH: null 필드는 미변경</b> (08 §B, v1.6 보강).
 *
 * <p>직책·전화번호는 빈 문자열로 비운다. 이름·이메일은 NOT NULL이라 비울 수 없다 —
 * {@code @Email}만으로는 빈 문자열이 통과해 {@code @Pattern}을 함께 건다.
 *
 * <p>대표 지정은 이 요청이 아니라 {@code POST .../contacts/{cid}/set-primary}다 (CU-11).
 * 지정 시 기존 대표가 자동 해제되며, <b>대표 해제만 하는 동작은 없다</b> — 대표 0명을 막기 위해서다.
 */
public record UpdateContactRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String name,
        String title,
        String phone,
        @Email @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String email) {}
