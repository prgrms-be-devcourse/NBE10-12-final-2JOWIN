package com.twojo.activity.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 할 일 응답 (AC-09).
 *
 * <p>{@code doneAt}이 {@code null}이면 미완료다 — 별도 상태 컬럼을 두지 않는다.
 * 배정 필드가 없다 (Q-29, v1.5에서 {@code assigneeMemberId} 제거).
 */
public record TaskResponse(
        UUID id,
        UUID dealId,
        String content,
        LocalDate dueDate,
        Instant doneAt) {}
