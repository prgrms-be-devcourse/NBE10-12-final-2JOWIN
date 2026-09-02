package com.twojo.activity.dto;

import java.time.Instant;

/**
 * 상담 기록 수정 요청 (AC-04).
 *
 * <p><b>PATCH: null 필드는 미변경</b> (08 §B 주석 · 11 §1.3) — 보낸 필드만 바꾼다.
 *
 * <p><b>작성자 본인만 수정할 수 있다.</b> 타인 것은 404 {@code ACTIVITY_NOT_AUTHOR}로 막는다 —
 * 403이 아니라 404인 이유는 존재 여부를 노출하지 않기 위해서다 (SC-09).
 * 판정 축은 {@code author_member_id}이며 관리자도 예외가 없다.
 */
public record UpdateActivityRequest(
        String channel,
        String content,
        Instant occurredAt) {}
