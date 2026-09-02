package com.twojo.deal.repository;

import com.twojo.deal.entity.Deal;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Deal 목록 조건 조립 (DL-06·13·14).
 *
 * <p>회사 스코프와 미삭제는 <b>항상</b> 걸린다 — 선택 필터가 아니라 기반 조건이다 (SC-01, §1.5).
 * 나머지 셋(stage·assignee·customer)은 null이면 조건에서 빠진다.
 *
 * <p><b>영업(OWNED_ONLY)의 범위 제한은 여기서 하지 않는다.</b> 서비스가 스코프를 보고
 * assigneeMemberId를 본인으로 고정해 넘긴다 — 범위 판정을 조건 조립에 섞으면
 * 필터를 하나 빠뜨렸을 때 격리가 조용히 뚫린다 (SC-02).
 */
final class DealSpecs {

    private DealSpecs() {
    }

    /**
     * @param stage            null이면 전 단계 (보드는 단계별로 나눠 호출한다)
     * @param assigneeMemberId null이면 담당자 무관 — 영업 요청은 서비스가 본인 id로 채워 넘긴다
     * @param customerId       null이면 고객사 무관
     */
    static Specification<Deal> search(UUID companyId, Deal.Stage stage,
                                      UUID assigneeMemberId, UUID customerId) {
        Specification<Deal> spec = notDeletedIn(companyId);
        spec = andEquals(spec, "stage", stage);
        spec = andEquals(spec, "assigneeMemberId", assigneeMemberId);
        spec = andEquals(spec, "customerId", customerId);
        return spec;
    }

    /**
     * 값이 null이면 조건을 붙이지 않는다.
     * <p>{@code Specification.and(null)}은 예외를 던지므로 null을 그대로 넘기면 안 된다 —
     * 필터를 비운 요청이 500이 된다.
     */
    private static Specification<Deal> andEquals(Specification<Deal> spec, String field, Object value) {
        return value == null ? spec : spec.and((root, query, cb) -> cb.equal(root.get(field), value));
    }

    /** 기반 조건 — 회사 스코프 + 미삭제. 어떤 목록 조회도 이걸 건너뛰지 않는다 */
    private static Specification<Deal> notDeletedIn(UUID companyId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("companyId"), companyId),
                cb.isNull(root.get("deletedAt")));
    }
}
