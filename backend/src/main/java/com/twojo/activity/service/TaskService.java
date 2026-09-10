package com.twojo.activity.service;

import com.twojo.activity.dto.CreateTaskRequest;
import com.twojo.activity.dto.TaskResponse;
import com.twojo.activity.dto.UpdateTaskRequest;
import com.twojo.activity.entity.Task;
import com.twojo.activity.repository.TaskRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다음 할 일 (AC-09).
 *
 * <p><b>배정 대상이 없다</b> (Q-29) — 할 일은 Deal의 자식이라 "내 할 일"은 내가 담당하는 Deal의
 * 미완료 할 일로 파생된다. 담당이 이관되면 할 일도 Deal을 따라 옮겨간다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final DealAccess dealAccess;

    /** 등록 (AC-09) — 미완료로 시작한다. {@code doneAt}이 null인 것이 곧 미완료다. */
    @Transactional
    public TaskResponse create(AccessContext ctx, UUID dealId, CreateTaskRequest request) {
        dealAccess.requireInScope(ctx, dealId);
        return toResponse(taskRepository.save(
                Task.create(ctx.companyId(), dealId, request.content(), request.dueDate())));
    }

    /**
     * 완료 처리·수정 (AC-09) — null 필드는 바꾸지 않는다.
     *
     * <p><b>{@code done = false}는 미변경이다.</b> 완료 취소는 03·07 어디에도 없고
     * {@code Task}에 되돌리는 메서드도, 거절할 에러 코드도 없다. 화면도 완료 버튼 하나다.
     * 지원하려면 엔티티 메서드와 에러 코드가 함께 들어와야 한다.
     */
    @Transactional
    public TaskResponse update(AccessContext ctx, UUID taskId, UpdateTaskRequest request, Instant now) {
        Task task = findInScope(ctx, taskId);
        task.update(request.content(), request.dueDate());
        if (Boolean.TRUE.equals(request.done())) {
            task.complete(now);
        }
        return toResponse(task);
    }

    /**
     * 할 일이 이 요청의 범위 안에 있는지 — 아니면 404 (SC-09).
     *
     * <p>영업은 <b>담당 Deal의 할 일만</b> 본다 (09 §61 · Q-29 "내 할 일" 정의). 기업 관리자는 담당을
     * 묻지 않고 회사 범위에서 찾는다 — 회사 전체 Deal id를 받아 IN 절에 넣는 것이 답이 아니다.
     */
    private Task findInScope(AccessContext ctx, UUID taskId) {
        if (ctx.scope() == AccessScope.COMPANY_ALL) {
            return taskRepository.findByIdAndCompanyId(taskId, ctx.companyId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        }
        List<UUID> dealIds = dealAccess.assignedDealIds(ctx);
        if (dealIds.isEmpty()) {
            // 빈 IN 절은 처리 방식이 환경에 따라 다르다. 조회하지 않는다
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return taskRepository.findByIdAndCompanyIdAndDealIdIn(taskId, ctx.companyId(), dealIds)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getDealId(), task.getContent(),
                task.getDueDate(), task.getDoneAt());
    }
}
