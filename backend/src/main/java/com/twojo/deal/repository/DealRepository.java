package com.twojo.deal.repository;

import com.twojo.deal.entity.Deal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Deal 조회 (DL) — 모든 조회에 회사 스코프와 소프트 삭제 조건이 함께 걸린다 (SC-01, docs/11 §1.5).
 *
 * <p>목록(DL-06·13·14)은 선택 필터가 셋(stage·assignee·customer)이라 파생 쿼리로는 조합이
 * 폭발한다 — 그 하나만 {@link JpaSpecificationExecutor}로 조립한다 ({@link DealSpecs}).
 * 나머지는 파생 쿼리로 의도를 이름에 드러낸다.
 */
public interface DealRepository extends JpaRepository<Deal, UUID>, JpaSpecificationExecutor<Deal> {

    /**
     * 목록·보드 (DL-06·13·14) — 회사 스코프와 미삭제는 항상, 나머지 셋은 null이면 조건에서 빠진다.
     *
     * <p><b>영업(OWNED_ONLY)의 범위 제한은 서비스가 {@code assigneeMemberId}를 본인으로
     * 고정해 넘기는 방식이다</b> (SC-02). 여기서 스코프를 다시 판정하지 않는다.
     * 정렬은 엔드포인트별 기본값 고정 (Q-39) — 호출부의 Pageable이 정한다.
     */
    default Page<Deal> search(UUID companyId, Deal.Stage stage,
                              UUID assigneeMemberId, UUID customerId, Pageable pageable) {
        return findAll(DealSpecs.search(companyId, stage, assigneeMemberId, customerId), pageable);
    }

    /** 회사 스코프 + 미삭제. 조건에 맞지 않으면 빈 Optional — 호출부에서 404로 변환한다 (SC-09) */
    Optional<Deal> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

    /**
     * 회사 스코프 없는 단건 조회 — {@code DealQuery.assigneeIdOf}·{@code isOpen} 전용이다.
     *
     * <p>이 두 계약은 호출자(B·D)가 이미 회사 안에서 얻은 dealId를 넘기는 자리라
     * companyId를 받지 않는다 (docs/11 §7.2). <b>구성원 요청을 직접 받는 경로에서는 쓰지 않는다</b> —
     * 거기서는 위의 회사 스코프 버전을 쓴다.
     */
    Optional<Deal> findByIdAndDeletedAtIsNull(UUID id);

    /** B의 CU-08 — 진행 중 Deal이 하나라도 있으면 고객사를 삭제할 수 없다 */
    boolean existsByCustomerIdAndStageInAndDeletedAtIsNull(UUID customerId, Collection<Deal.Stage> stages);

    /** B의 CU-12 — 고객사 상세의 Deal 이력. 종결 Deal도 포함한다 */
    List<Deal> findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID customerId);

    /**
     * B의 DB-04·05 — 활동·할 일 목록에 붙일 딜 제목 배치 조회.
     * 없는 id는 결과에서 빠진다(소프트 삭제된 Deal의 활동이 남아 있을 수 있다).
     */
    List<Deal> findByCompanyIdAndIdInAndDeletedAtIsNull(UUID companyId, Collection<UUID> ids);

    /**
     * B의 SC-02 범위 필터 — 담당 Deal id 전체. 종결(WON·LOST)도 포함한다 (최근 활동은 성사 딜도 보여준다).
     * 제목·단계가 필요 없는 자리라 id만 투영해 목록을 가볍게 유지한다.
     */
    @Query("""
            select d.id from Deal d
            where d.companyId = :companyId
              and d.assigneeMemberId = :memberId
              and d.deletedAt is null
            """)
    List<UUID> findIdsByAssignee(@Param("companyId") UUID companyId, @Param("memberId") UUID memberId);

    /**
     * A의 MB-14 — 진행 중 담당 Deal 건수. 위 {@code findIdsByAssignee}와 달리 종결(WON·LOST)을 <b>제외</b>한다 —
     * 비활성화의 "이관 대상 필수" 판정은 실제로 옮길 Deal만 세야 한다. {@code ix_deal_company_assignee_stage}를 탄다.
     */
    long countByCompanyIdAndAssigneeMemberIdAndStageInAndDeletedAtIsNull(
            UUID companyId, UUID assigneeMemberId, Collection<Deal.Stage> stages);

    /**
     * A의 MB-14 — 이관할 진행 중 담당 Deal 엔티티. 위 count와 <b>같은 조건</b>이라 사전 판정 건수와 옮긴 건수가 일치한다.
     * 엔티티로 읽는 이유는 {@code DealCommand.reassignOpenDeals}의 규약이다 — JPQL 일괄 update는
     * {@code @Version}·{@code updated_at}을 건드리지 않아 열어 둔 딜 상세의 낙관적 락(DL-05)이 이관을 알아채지 못한다.
     */
    List<Deal> findByCompanyIdAndAssigneeMemberIdAndStageInAndDeletedAtIsNull(
            UUID companyId, UUID assigneeMemberId, Collection<Deal.Stage> stages);

    /**
     * D의 대시보드 파이프라인 집계 (DB-01) — 단계별 건수와 예상 금액 합.
     *
     * <p><b>{@code assigneeMemberId}가 null이면 회사 전체</b>다 (기업 관리자, SC-05). 영업이면 본인 담당만
     * 넘어온다 (SC-02) — 스코프 판정은 {@code SalesStatsQueryImpl}이 하고 여기서는 걸린 값을 쓰기만 한다.
     *
     * <p><b>건수가 0인 단계는 결과에 없다</b> — {@code group by}의 성질이다. 네 단계를 항상 채우는 것은
     * 호출자 몫이고, 그래야 "리드 0건"이 화면에서 빈칸이 아니라 0으로 보인다.
     *
     * <p>{@code expectedAmount}는 nullable이라(DL-02 미정 허용) {@code coalesce}로 0을 채운다 —
     * 전부 null인 단계에서 합이 null로 나오면 소비자가 다시 방어해야 한다.
     */
    @Query("""
            select d.stage as stage, count(d) as count, coalesce(sum(d.expectedAmount), 0) as amount
            from Deal d
            where d.companyId = :companyId
              and d.deletedAt is null
              and d.stage in :stages
              and (:assigneeMemberId is null or d.assigneeMemberId = :assigneeMemberId)
            group by d.stage
            """)
    List<StageAggregate> aggregateByStage(@Param("companyId") UUID companyId,
                                          @Param("assigneeMemberId") UUID assigneeMemberId,
                                          @Param("stages") Collection<Deal.Stage> stages);

    /**
     * 담당자별 진행 중 딜 수 (DB-06) — 회사 전체를 한 번에 센다.
     *
     * <p><b>기간과 무관한 현재 스냅샷이다</b> (2026-09-10 D 확정). "그때 진행 중이었던"을 재구성하려면
     * 전이 이력이 필요한데 그건 전환율(DB-07)과 같은 블로커이고, 화면 의도도 "지금 몇 건 안고 있나"다.
     *
     * <p>담당자별로 한 번씩 세면 구성원 수만큼 쿼리가 나간다 — 그래서 group by로 한 번에 받는다.
     * 진행 딜이 없는 구성원은 결과에 없으므로 0을 채우는 것은 호출자 몫이다.
     */
    @Query("""
            select d.assigneeMemberId as memberId, count(d) as count
            from Deal d
            where d.companyId = :companyId
              and d.deletedAt is null
              and d.stage in :stages
            group by d.assigneeMemberId
            """)
    List<AssigneeCount> countOpenByAssignee(@Param("companyId") UUID companyId,
                                            @Param("stages") Collection<Deal.Stage> stages);

    /** {@link #countOpenByAssignee} 투영 */
    interface AssigneeCount {
        UUID getMemberId();

        long getCount();
    }

    /** {@link #aggregateByStage} 투영 — {@code Object[]}로 받으면 호출부가 인덱스로 캐스팅하게 된다 */
    interface StageAggregate {
        Deal.Stage getStage();

        long getCount();

        Long getAmount();
    }
}
