package com.twojo.order.repository;

import com.twojo.order.entity.Order;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 주문 조회 (OD) — 모든 조회에 회사 스코프가 걸린다 (SC-01, docs/11 §1.5).
 *
 * <p>목록(OD-08)은 기간 필터 둘에 담당 축 제한까지 얹혀서 파생 쿼리로는 조합이 폭발한다 —
 * 그 하나만 {@link JpaSpecificationExecutor}로 조립한다 ({@link OrderSpecs}).
 */
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    /**
     * 목록 (OD-08) — 회사 스코프는 항상, 나머지는 null이면 조건에서 빠진다.
     * 항목은 싣지 않는다 — 목록에 필요한 것은 금액 합계뿐이고, 주문마다 항목을 끌어오면 N+1이 된다.
     */
    default Page<Order> search(UUID companyId, Instant from, Instant toExclusive,
                               Collection<UUID> visibleQuoteIds, Pageable pageable) {
        return findAll(OrderSpecs.search(companyId, from, toExclusive, visibleQuoteIds), pageable);
    }

    /**
     * 기간 안에 전환된 주문 전부 (DB-02·06) — 목록과 <b>같은 조건 조립</b>을 페이지 없이 쓴다.
     *
     * <p>{@link OrderSpecs}를 다시 쓰는 이유는 <b>빈 컬렉션 처리</b> 때문이다. 거기서 빈 목록을
     * 거짓 조건으로 바꾸는데, 집계에서 그걸 빠뜨리면 담당 딜이 없는 영업에게 회사 전체 성사액이
     * 잡힌다 — 같은 규칙을 두 벌로 두지 않는다.
     *
     * <p>투영이 아니라 엔티티를 읽는다 — 대시보드 한 달치라 크기가 제한적이고,
     * 조건 조립을 그대로 재사용하는 쪽이 {@code (:ids is null or ...)} 같은 JPQL 널 바인딩보다 안전하다.
     */
    default List<Order> findConverted(UUID companyId, Instant from, Instant toExclusive,
                                      Collection<UUID> visibleQuoteIds) {
        return findAll(OrderSpecs.search(companyId, from, toExclusive, visibleQuoteIds));
    }

    /**
     * 상세 (OD-09) — 스냅샷 항목까지 한 번에 가져온다.
     * 조건에 맞지 않으면 빈 Optional — 호출부에서 404로 변환한다 (SC-09).
     */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * 재전환 차단 (OD-03) — <b>회사 스코프가 없다.</b> {@code quote_id}에 걸린 UNIQUE가
     * 회사 구분 없는 전역 제약이라, 여기에 회사 조건을 더하면 검사와 제약의 기준이 갈린다.
     * 호출자는 이 검사 앞에서 이미 견적의 회사를 확인했다.
     */
    boolean existsByQuoteId(UUID quoteId);
}
