package com.twojo.quote.repository;

import com.twojo.quote.entity.Quote;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * 견적 목록 조건 조립 (QT-20).
 *
 * <p>회사 스코프는 <b>항상</b> 걸린다 — 선택 필터가 아니라 기반 조건이다 (SC-01).
 * {@code quote}에는 소프트 삭제가 없다 — 삭제 대상은 고객사·Deal·상담 기록뿐이다 (ERD 설계 원칙).
 *
 * <p><b>영업의 범위 제한은 담당 Deal id 목록으로 들어온다</b> (SC-02). 견적에는 담당자 컬럼이
 * 없고 범위가 Deal에서 파생하기 때문이다 — 서비스가 {@code DealQuery.assignedDealIds}로 받아
 * 넘긴다. 여기서 스코프를 다시 판정하지 않는다.
 */
final class QuoteSpecs {

    private QuoteSpecs() {
    }

    /**
     * @param status         null이면 전 상태
     * @param dealId         null이면 딜 무관 — 특정 딜의 견적 목록(QT-18)에 쓴다
     * @param visibleDealIds <b>null이면 제한 없음</b>(기업 관리자). 목록이면 그 안의 딜만.
     *                       <b>빈 목록이면 아무것도 안 보인다</b> — 담당 딜이 하나도 없는 영업이다
     */
    static Specification<Quote> search(UUID companyId, Quote.Status status,
                                       UUID dealId, Collection<UUID> visibleDealIds) {
        Specification<Quote> spec = inCompany(companyId);
        spec = andEquals(spec, "status", status);
        spec = andEquals(spec, "dealId", dealId);
        return visibleDealIds == null ? spec : spec.and(dealIdIn(visibleDealIds));
    }

    /**
     * 값이 null이면 조건을 붙이지 않는다.
     * <p>{@code Specification.and(null)}은 예외를 던지므로 null을 그대로 넘기면 안 된다 —
     * 필터를 비운 요청이 500이 된다 (#46에서 실제로 겪었다).
     */
    private static Specification<Quote> andEquals(Specification<Quote> spec, String field, Object value) {
        return value == null ? spec : spec.and((root, query, cb) -> cb.equal(root.get(field), value));
    }

    /**
     * 담당 딜 제한 — <b>빈 목록은 "전부"가 아니라 "아무것도"다.</b>
     *
     * <p>빈 컬렉션을 {@code in}에 그대로 넘기면 방언에 따라 결과가 갈린다.
     * 여기서 명시적으로 거짓 조건을 만들어, 담당 딜이 없는 영업에게 회사 전체가 보이는
     * 사고를 구조적으로 막는다.
     */
    private static Specification<Quote> dealIdIn(Collection<UUID> dealIds) {
        return dealIds.isEmpty()
                ? (root, query, cb) -> cb.disjunction()
                : (root, query, cb) -> root.get("dealId").in(dealIds);
    }

    /** 기반 조건 — 회사 스코프. 어떤 목록 조회도 이걸 건너뛰지 않는다 (SC-01) */
    private static Specification<Quote> inCompany(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }
}
