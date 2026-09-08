package com.twojo.order.repository;

import com.twojo.order.entity.Order;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * 주문 목록 조건 조립 (OD-08).
 *
 * <p>회사 스코프는 <b>항상</b> 걸린다 — 선택 필터가 아니라 기반 조건이다 (SC-01).
 * {@code orders}에는 소프트 삭제가 없다 — 삭제 대상은 고객사·Deal·상담 기록뿐이다 (ERD 설계 원칙).
 *
 * <p><b>영업의 범위 제한은 견적 id 목록으로 들어온다</b> (SC-04). 주문에는 담당자 컬럼도
 * {@code deal_id}도 없고 범위가 Deal에서 파생하기 때문이다 (09 §80) — 서비스가
 * {@code DealQuery.assignedDealIds} → {@code QuoteQuery.quoteIdsByDeals}로 받아 넘긴다.
 * 여기서 스코프를 다시 판정하지 않는다. {@code QuoteSpecs}가 딜 id로 하는 일과 같은 구조다.
 */
final class OrderSpecs {

    private OrderSpecs() {
    }

    /**
     * @param from            전환일 하한(포함). null이면 조건에서 빠진다
     * @param toExclusive     전환일 상한(<b>제외</b>) — 서비스가 to일의 다음 날 0시로 만들어 넘긴다
     * @param visibleQuoteIds <b>null이면 제한 없음</b>(기업 관리자).
     *                        <b>빈 목록이면 아무것도 안 보인다</b> — 담당 Deal이 하나도 없는 영업이다
     */
    static Specification<Order> search(UUID companyId, Instant from, Instant toExclusive,
                                       Collection<UUID> visibleQuoteIds) {
        Specification<Order> spec = inCompany(companyId);
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (toExclusive != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), toExclusive));
        }
        return visibleQuoteIds == null ? spec : spec.and(quoteIdIn(visibleQuoteIds));
    }

    /**
     * 범위 제한 — <b>빈 목록은 "전부"가 아니라 "아무것도"다.</b>
     * <p>빈 컬렉션을 {@code in}에 그대로 넘기면 방언에 따라 결과가 갈린다. 여기서 명시적으로
     * 거짓 조건을 만들어, 담당 딜이 없는 영업에게 회사 전체 주문이 보이는 사고를 구조적으로 막는다.
     */
    private static Specification<Order> quoteIdIn(Collection<UUID> quoteIds) {
        return quoteIds.isEmpty()
                ? (root, query, cb) -> cb.disjunction()
                : (root, query, cb) -> root.get("quoteId").in(quoteIds);
    }

    /** 기반 조건 — 회사 스코프. 어떤 목록 조회도 이걸 건너뛰지 않는다 (SC-01) */
    private static Specification<Order> inCompany(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }
}
