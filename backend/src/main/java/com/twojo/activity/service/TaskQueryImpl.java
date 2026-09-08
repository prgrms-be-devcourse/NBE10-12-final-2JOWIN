package com.twojo.activity.service;

import com.twojo.activity.entity.Task;
import com.twojo.activity.repository.TaskRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.TaskQuery;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 후속 필요 조회 (DB-05) — {@link TaskQuery} 구현.
 *
 * <p>미완료 할 일만 마감 임박순으로 준다. 마감이 지난 것도 포함한다 — 정렬이 임박순이라
 * 가장 오래 밀린 것이 맨 위에 온다 (이슈 #158 설계 결정 3).
 *
 * <p>딜 제목은 여기서 붙이지 않는다 — 소비자가 조립한다 (11 §7.2).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQueryImpl implements TaskQuery {

    private final TaskRepository taskRepository;
    private final DealQuery dealQuery;

    @Override
    public List<FollowUpSummary> followUps(AccessContext ctx, int limit) {
        requireValidLimit(limit);

        Pageable page = PageRequest.of(0, limit);
        List<Task> rows;

        if (ctx.scope() == AccessScope.COMPANY_ALL) {
            // 관리자는 담당 딜을 묻지 않는다 — 회사 전체 딜 id를 받아 IN 절에 넣는 것이 답이 아니다
            rows = taskRepository.findByCompanyIdAndDoneAtIsNullOrderByDueDateAscIdAsc(
                    ctx.companyId(), page);
        } else {
            List<UUID> dealIds = dealQuery.assignedDealIds(ctx.companyId(), ctx.memberId());
            if (dealIds.isEmpty()) {
                // 빈 IN 절은 처리 방식이 환경에 따라 다르다. 조회하지 않는다
                return List.of();
            }
            rows = taskRepository.findByCompanyIdAndDealIdInAndDoneAtIsNullOrderByDueDateAscIdAsc(
                    ctx.companyId(), dealIds, page);
        }

        return rows.stream()
                .map(t -> new FollowUpSummary(t.getId(), t.getDealId(), t.getContent(), t.getDueDate()))
                .toList();
    }

    /**
     * 계약이 정한 범위 밖이면 프로그래밍 오류로 드러낸다 — 잘라내 넘기지 않는다.
     * 이 값은 사용자 입력이 아니라 호출부가 정하는 상수다 (TaskQuery javadoc).
     */
    private static void requireValidLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit은 1 이상 " + MAX_LIMIT + " 이하여야 한다: " + limit);
        }
    }
}
