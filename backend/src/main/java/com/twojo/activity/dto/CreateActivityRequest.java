package com.twojo.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * 상담 기록 등록 요청 (AC-01~03).
 *
 * <p>{@code channel}은 {@code CALL} / {@code MEETING} / {@code EMAIL} 중 하나다 (AC-02).
 * {@code occurredAt}은 <b>기록한 시각이 아니라 실제로 상담이 일어난 시각</b>이다 (AC-03) —
 * 어제 만난 것을 오늘 입력할 수 있어야 하므로 클라이언트가 보낸다.
 *
 * <p>작성자는 서버가 AccessContext에서 채운다 — 요청 바디에 없다.
 */
public record CreateActivityRequest(
        @NotBlank String channel,
        @NotBlank String content,
        @NotNull Instant occurredAt) {}
