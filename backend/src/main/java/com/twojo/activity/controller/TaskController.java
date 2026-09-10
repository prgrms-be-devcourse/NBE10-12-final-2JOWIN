package com.twojo.activity.controller;

import com.twojo.activity.dto.CreateTaskRequest;
import com.twojo.activity.dto.TaskResponse;
import com.twojo.activity.dto.UpdateTaskRequest;
import com.twojo.activity.service.TaskService;
import com.twojo.boundary.AccessContext;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다음 할 일 (07 §B · AC-09).
 *
 * <p>등록은 Deal 아래, 수정은 할 일 단건이라 경로 앞머리가 둘이다 — 상담 기록과 같은 형태로
 * 클래스 매핑을 {@code /api/v1}에 둔다.
 *
 * <p>범위 판정은 서비스가 한다. 배정 개념이 없어 "내 할 일"이 담당 Deal에서 파생된다 (Q-29).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** 등록 (AC-09) — 예정일이 필수다. 배정 대상은 없다 (Q-29) */
    @PostMapping("/deals/{dealId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(AccessContext ctx, @PathVariable UUID dealId,
                               @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(ctx, dealId, request);
    }

    /** 완료 처리·수정 — null 필드는 미변경. {@code done = false}는 완료를 되돌리지 않는다 */
    @PatchMapping("/tasks/{taskId}")
    public TaskResponse update(AccessContext ctx, @PathVariable UUID taskId,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(ctx, taskId, request, Instant.now());
    }
}
