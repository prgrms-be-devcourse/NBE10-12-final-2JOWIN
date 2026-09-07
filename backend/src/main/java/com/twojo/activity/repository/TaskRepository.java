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
 * <p>범위 축이 둘이다 — 영업(OWNED_ONLY)은 담당 딜 범위, 기업 관리자(COMPANY_ALL)는 회사 범위다.
 * 딜 범위는 C의 {@code DealQuery.assignedDealIds()}가 준 목록으로, 회사 범위는 {@code company_id}로 건다
 * (ERD v1.6.5).
 *
 * <p><b>⚠️ 상속된 {@code findById(UUID)}를 쓰지 말 것.</b> 회사 조건이 빠져 타사 할 일이 나온다.
 * 단건 접근은 아래 둘 중 하나로 범위를 건다.
 */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /**
     * 후속 필요 (DB-05) — 미완료만, 마감 임박순. 동률은 id로 안정화한다.
     *
     * <p>{@code dealIds}가 이미 회사로 걸러진 목록이지만 회사 조건을 함께 건다 —
     * 13 §2 셀프 체크리스트가 <b>"모든 조회에 회사 스코프"</b>로 정했고,
     * {@code ActivityRepository}도 목록에 회사를 첫 조건으로 건다.
     */
    List<Task> findByCompanyIdAndDealIdInAndDoneAtIsNullOrderByDueDateAscIdAsc(
            UUID companyId, Collection<UUID> dealIds);

    /**
     * 단건 접근 — <b>담당 딜 범위</b> (영업, OWNED_ONLY).
     *
     * <p>{@code dealIds}는 {@code DealQuery.assignedDealIds(companyId, memberId)}가 준 목록이라
     * 이미 회사로 걸러져 있지만, 회사 조건을 한 번 더 건다 — 06 §접근 범위가
     * <b>"단건 조회에 회사·딜 범위를 함께"</b>로 정했다.
     * 범위 밖 할 일이면 빈 {@code Optional}이 오고, 호출부는 404로 변환한다 (SC-09).
     */
    Optional<Task> findByIdAndCompanyIdAndDealIdIn(UUID id, UUID companyId, Collection<UUID> dealIds);

    /**
     * 단건 접근 — <b>회사 범위</b> (기업 관리자, COMPANY_ALL).
     *
     * <p>관리자는 회사 전체 딜 id를 IN 절에 넣는 것이 답이 아니라 회사 축으로 바로 건다.
     * 복합 FK가 "할 일의 회사 = 딜의 회사"를 보장하므로 이 조건만으로 테넌트 격리가 된다.
     */
    Optional<Task> findByIdAndCompanyId(UUID id, UUID companyId);
}
