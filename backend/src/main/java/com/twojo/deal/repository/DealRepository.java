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
}
