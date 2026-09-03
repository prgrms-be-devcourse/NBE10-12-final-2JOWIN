package com.twojo.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 다음 할 일 등록 요청 (AC-09).
 *
 * <p>배정 대상이 없다 (Q-29) — 할 일은 Deal의 자식이라 "내 할 일"은 <b>내가 담당하는 Deal의
 * 미완료 할 일</b>로 파생된다. 담당이 이관되면 할 일도 Deal을 따라 자동으로 옮겨간다.
 *
 * <p>{@code dueDate}는 필수다 — AC-09가 "할 일과 예정일"을 함께 요구한다.
 */
public record CreateTaskRequest(
        @NotBlank String content,
        @NotNull LocalDate dueDate) {}
