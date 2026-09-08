package com.twojo.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.activity.entity.Task;
import com.twojo.activity.repository.TaskRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.Role;
import com.twojo.boundary.TaskQuery;
import com.twojo.boundary.TaskQuery.FollowUpSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 대시보드 후속 필요 (DB-05) — 미완료 할 일만, 마감 임박순.
 *
 * <p>범위 분기는 최근 활동과 같다. 미완료 조건과 정렬은 파생 쿼리 이름이 만드는 SQL의 몫이라
 * 목으로는 확인되지 않는다 — 로컬 실행으로 따로 확인한다 (이슈 #158 설계 결정 5·6).
 */
@ExtendWith(MockitoExtension.class)
class TaskQueryImplTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    private static final AccessContext ADMIN =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private TaskRepository taskRepository;
    @Mock private DealQuery dealQuery;
    @InjectMocks private TaskQueryImpl taskQuery;

    private static Task 할일(String content) {
        return 할일(content, UUID.randomUUID());
    }

    /**
     * id는 저장될 때 생기므로 목이 만든 엔티티에는 없다. {@code taskId}·{@code dealId}가 뒤바뀌어도
     * 둘 다 UUID라 컴파일이 되므로, 그 실수를 잡으려면 서로 다른 값을 심어야 한다.
     */
    private static Task 할일(String content, UUID id) {
        Task task = Task.create(COMPANY_ID, DEAL_ID, content, LocalDate.of(2026, 8, 26));
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    @Test
    @DisplayName("기업 관리자는 회사 전체 할 일을 받는다 — 담당 딜을 묻지 않는다 (SC-05)")
    void followUps_admin_seesWholeCompany() {
        // limit이 그대로 쿼리에 실려야 한다 — any()로 두면 상수로 바꿔치기해도 안 잡힌다
        given(taskRepository.findByCompanyIdAndDoneAtIsNullOrderByDueDateAscIdAsc(
                COMPANY_ID, PageRequest.of(0, 10)))
                .willReturn(List.of(할일("성원산업 재방문 일정 조율", TASK_ID)));

        List<FollowUpSummary> result = taskQuery.followUps(ADMIN, 10);

        assertThat(result.get(0).content()).isEqualTo("성원산업 재방문 일정 조율");
        assertThat(result.get(0).taskId()).isEqualTo(TASK_ID);
        assertThat(result.get(0).dealId()).isEqualTo(DEAL_ID);
        assertThat(result.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        then(dealQuery).should(never()).assignedDealIds(any(), any());
    }

    @Test
    @DisplayName("영업 담당자는 본인 담당 Deal의 할 일만 받는다 (SC-02·04)")
    void followUps_salesRep_filteredByAssignedDeals() {
        List<UUID> 담당딜 = List.of(DEAL_ID);
        given(dealQuery.assignedDealIds(COMPANY_ID, MEMBER_ID)).willReturn(담당딜);
        given(taskRepository.findByCompanyIdAndDealIdInAndDoneAtIsNullOrderByDueDateAscIdAsc(
                COMPANY_ID, 담당딜, PageRequest.of(0, 10)))
                .willReturn(List.of(할일("성원산업 재방문 일정 조율")));

        // G7: 결과를 버리고 빈 목록을 돌려줘도 never()만으로는 안 잡힌다
        List<FollowUpSummary> result = taskQuery.followUps(SALES, 10);
        assertThat(result).singleElement()
                .extracting(FollowUpSummary::content).isEqualTo("성원산업 재방문 일정 조율");

        then(taskRepository).should(never())
                .findByCompanyIdAndDoneAtIsNullOrderByDueDateAscIdAsc(any(), any());
    }

    @Test
    @DisplayName("담당 Deal이 없으면 조회하지 않고 빈 목록을 돌려준다 — 빈 IN 절을 만들지 않는다")
    void followUps_salesRepWithNoDeals_skipsQuery() {
        given(dealQuery.assignedDealIds(COMPANY_ID, MEMBER_ID)).willReturn(List.of());

        List<FollowUpSummary> result = taskQuery.followUps(SALES, 10);

        // 목을 부르지 않는 경로다 — 스텁이 준 값이 아니라 구현이 만든 빈 목록을 확인한다
        assertThat(result).isEmpty();
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("limit이 1~MAX_LIMIT 밖이면 IllegalArgumentException — 호출부가 정하는 상수라 잘라내지 않는다")
    void followUps_limitOutOfRange_throws() {
        assertThatThrownBy(() -> taskQuery.followUps(ADMIN, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> taskQuery.followUps(ADMIN, TaskQuery.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);

        then(taskRepository).shouldHaveNoInteractions();
        then(dealQuery).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("경계값 1과 MAX_LIMIT은 통과한다 — 부등호가 밀리면 정상 요청이 막힌다")
    void followUps_limitAtBoundary_passes() {
        given(taskRepository.findByCompanyIdAndDoneAtIsNullOrderByDueDateAscIdAsc(eq(COMPANY_ID), any()))
                .willReturn(List.of());

        assertThatNoException().isThrownBy(() -> taskQuery.followUps(ADMIN, 1));
        assertThatNoException().isThrownBy(() -> taskQuery.followUps(ADMIN, TaskQuery.MAX_LIMIT));
    }
}
