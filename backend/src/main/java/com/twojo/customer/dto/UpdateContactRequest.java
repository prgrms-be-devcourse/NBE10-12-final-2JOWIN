package com.twojo.customer.dto;

import jakarta.validation.constraints.Email;

/**
 * 고객사 담당자 수정 요청.
 *
 * <p><b>PATCH: null 필드는 미변경</b> (08 §B 주석 · 11 §1.3) — 필드 하나만 골라 보내는 화면을
 * 전제하므로, 안 보낸 필드는 건드리지 않는다.
 *
 * <p>대표 지정은 이 요청이 아니라 {@code POST .../contacts/{cid}/set-primary}다 (CU-11).
 * 지정 시 기존 대표가 자동 해제되며, <b>대표 해제만 하는 동작은 없다</b> — 대표 0명을 막기 위해서다.
 */
public record UpdateContactRequest(
        String name,
        String title,
        String phone,
        @Email String email) {}
