package com.twojo.activity.dto;

import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/**
 * 상담 기록 수정 요청 (AC-04) — <b>PATCH: null 필드는 미변경</b> (08 §B, v1.6 보강).
 *
 * <p>채널 값 검증은 {@code @Pattern}이 한다 — 생성 요청과 같은 형태다 (08 §B).
 * 그래서 이 필드에는 공백 검사가 따로 없다. 값 검증이 그것을 겸한다.
 *
 * <p><b>작성자 본인만 수정할 수 있다.</b> 타인 것은 404 {@code ACTIVITY_NOT_AUTHOR}로 막는다 —
 * 403이 아니라 404인 이유는 존재 여부를 노출하지 않기 위해서다 (SC-09).
 * 판정 축은 {@code author_member_id}이며 관리자도 예외가 없다.
 */
public record UpdateActivityRequest(
        @Pattern(regexp = "CALL|MEETING|EMAIL",
                 message = "수단은 CALL·MEETING·EMAIL 중 하나입니다.") String channel,
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String content,
        Instant occurredAt) {}
