package com.twojo.activity.repository;

import com.twojo.activity.entity.Task;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
 *
 * <p><b>⚠️ 상속된 {@code findById(UUID)}를 쓰지 말 것.</b> 회사 조건이 없고, 찾아온
 * {@code Task}에 {@code companyId}가 없어 사후 검증도 불가능하다. {@code activity}는
 * {@code company_id} 컬럼과 복합 FK로 DB가 막아주지만 {@code task}는 둘 다 없다 —
 * 단건 접근은 반드시 아래 {@link #findByIdAndDealIdIn}으로 부모 딜 범위를 함께 건다.
 */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** 후속 필요 (DB-05) — 미완료만, 마감 임박순. 동률은 id로 안정화한다. */
    List<Task> findByDealIdInAndDoneAtIsNullOrderByDueDateAscIdAsc(Collection<UUID> dealIds);

    /**
     * 단건 접근 (수정·완료 처리) — <b>부모 딜 범위를 함께 건다.</b>
     *
     * <p>{@code dealIds}는 {@code DealQuery.assignedDealIds(companyId, memberId)}가 준 목록이다.
     * 범위 밖 할 일이면 빈 {@code Optional}이 오고, 호출부는 404로 변환한다 (SC-09).
     *
     * <p>기업 관리자(COMPANY_ALL)는 이 메서드로 덮을 수 없다 — 회사 전체 딜 id를 IN 절에 넣는 것이
     * 답이 아니기 때문이다. {@code task.company_id}가 확정되면 {@code findByIdAndCompanyId}로 갈린다.
     */
    Optional<Task> findByIdAndDealIdIn(UUID id, Collection<UUID> dealIds);
}
