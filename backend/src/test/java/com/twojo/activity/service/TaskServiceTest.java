package com.twojo.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.twojo.activity.dto.CreateTaskRequest;
import com.twojo.activity.dto.UpdateTaskRequest;
import com.twojo.activity.entity.Task;
import com.twojo.activity.repository.TaskRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 다음 할 일 (AC-09) — 배정 개념이 없어 범위가 Deal에서 파생한다 (Q-29).
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-10T01:00:00Z");

    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private TaskRepository taskRepository;
    @Mock private DealAccess dealAccess;
    @InjectMocks private TaskService taskService;

    @Test
    @DisplayName("담당 Deal이면 할 일을 만들고 미완료 상태로 둔다")
    void create_startsIncomplete() {
        given(taskRepository.save(any(Task.class))).willAnswer(call -> call.getArgument(0));

        var response = taskService.create(SALES, DEAL_ID,
                new CreateTaskRequest("견적서 보내기", LocalDate.of(2026, 9, 12)));

        assertThat(response.dealId()).isEqualTo(DEAL_ID);
        assertThat(response.content()).isEqualTo("견적서 보내기");
        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(response.doneAt()).isNull();
    }

    @Test
    @DisplayName("done=true면 완료 시각을 찍고 보낸 필드도 함께 바꾼다")
    void update_completesAndChangesFields() {
        Task task = Task.create(COMPANY_ID, DEAL_ID, "견적서 보내기", LocalDate.of(2026, 9, 12));
        given(dealAccess.assignedDealIds(SALES)).willReturn(List.of(DEAL_ID));
        given(taskRepository.findByIdAndCompanyIdAndDealIdIn(TASK_ID, COMPANY_ID, List.of(DEAL_ID)))
                .willReturn(Optional.of(task));

        var response = taskService.update(SALES, TASK_ID,
                new UpdateTaskRequest(null, LocalDate.of(2026, 9, 15), true), NOW);

        assertThat(response.doneAt()).isEqualTo(NOW);
        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(response.content()).isEqualTo("견적서 보내기");
    }

    /**
     * 완료 취소는 03·07 어디에도 없고 {@code Task}에 되돌리는 메서드도 없다.
     * 거절할 에러 코드가 없어 막지 않고 흘린다 — 지원하려면 엔티티와 에러 코드가 함께 와야 한다.
     */
    @Test
    @DisplayName("done=false는 완료를 되돌리지 않는다 — 미변경")
    void update_doneFalse_doesNotReopen() {
        Task task = Task.create(COMPANY_ID, DEAL_ID, "견적서 보내기", LocalDate.of(2026, 9, 12));
        task.complete(NOW);
        given(dealAccess.assignedDealIds(SALES)).willReturn(List.of(DEAL_ID));
        given(taskRepository.findByIdAndCompanyIdAndDealIdIn(TASK_ID, COMPANY_ID, List.of(DEAL_ID)))
                .willReturn(Optional.of(task));

        var response = taskService.update(SALES, TASK_ID,
                new UpdateTaskRequest(null, null, false), Instant.parse("2026-09-11T00:00:00Z"));

        assertThat(response.doneAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("기업 관리자는 담당을 묻지 않고 회사 범위에서 찾는다")
    void update_admin_looksUpByCompanyOnly() {
        AccessContext admin = new AccessContext(
                COMPANY_ID, UUID.randomUUID(), Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
        Task task = Task.create(COMPANY_ID, DEAL_ID, "견적서 보내기", LocalDate.of(2026, 9, 12));
        given(taskRepository.findByIdAndCompanyId(TASK_ID, COMPANY_ID)).willReturn(Optional.of(task));

        assertThat(taskService.update(admin, TASK_ID, new UpdateTaskRequest(null, null, true), NOW)
                .doneAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("담당 Deal이 하나도 없으면 조회하지 않고 404")
    void update_noAssignedDeals_throwsNotFound() {
        given(dealAccess.assignedDealIds(SALES)).willReturn(List.of());

        assertThatThrownBy(() -> taskService.update(SALES, TASK_ID,
                new UpdateTaskRequest(null, null, true), NOW))
                .isInstanceOf(BusinessException.class);

        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("범위 밖 할 일은 404 (SC-09)")
    void update_outOfScope_throwsNotFound() {
        given(dealAccess.assignedDealIds(SALES)).willReturn(List.of(DEAL_ID));
        given(taskRepository.findByIdAndCompanyIdAndDealIdIn(TASK_ID, COMPANY_ID, List.of(DEAL_ID)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(SALES, TASK_ID,
                new UpdateTaskRequest(null, null, true), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

}
