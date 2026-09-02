package com.twojo.activity.repository;

import com.twojo.activity.entity.Task;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 할 일 조회 (AC-09).
 *
 * <p><b>{@code task}에는 {@code company_id}가 없다.</b> Deal의 순수 자식이라 회사는 부모를 통해
 * 정해진다. 그래서 범위 조회는 딜 id 목록으로만 할 수 있고, 그 목록은 C의
 * {@code DealQuery.assignedDealIds()}에서 받는다.
 *
 * <p><b>관리자 범위(COMPANY_ALL) 조회는 아직 없다</b> — 회사 전체를 거를 축이 없어서다.
 * {@code task.company_id} 추가가 팀 논의 중이며, 결정되면 여기에 붙인다 (B 공유문서 2번).
 */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** 후속 필요 (DB-05) — 미완료만, 마감 임박순. 동률은 id로 안정화한다. */
    List<Task> findByDealIdInAndDoneAtIsNullOrderByDueDateAscIdAsc(Collection<UUID> dealIds);

    /** 딜의 할 일 목록 — 완료분 포함, 마감 임박순. */
    List<Task> findByDealIdOrderByDueDateAsc(UUID dealId);
}
