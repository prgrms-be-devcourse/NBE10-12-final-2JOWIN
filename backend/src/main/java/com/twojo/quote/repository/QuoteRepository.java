package com.twojo.quote.repository;

import com.twojo.quote.entity.Quote;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 견적 조회 (QT) — 모든 조회에 회사 스코프가 걸린다 (SC-01, docs/11 §1.5).
 *
 * <p>목록(QT-20)은 선택 필터가 둘(status·dealId)에 담당 딜 제한까지 얹혀서 파생 쿼리로는
 * 조합이 폭발한다 — 그 하나만 {@link JpaSpecificationExecutor}로 조립한다 ({@link QuoteSpecs}).
 */
public interface QuoteRepository extends JpaRepository<Quote, UUID>, JpaSpecificationExecutor<Quote> {

    /**
     * 목록 (QT-20) — 회사 스코프는 항상, 나머지는 null이면 조건에서 빠진다.
     * 항목은 싣지 않는다 — 목록에 필요한 것은 금액 합계뿐이고, 견적마다 항목을 끌어오면 N+1이 된다.
     */
    default Page<Quote> search(UUID companyId, Quote.Status status,
                               UUID dealId, Collection<UUID> visibleDealIds, Pageable pageable) {
        return findAll(QuoteSpecs.search(companyId, status, dealId, visibleDealIds), pageable);
    }

    /**
     * 상세 — 항목까지 한 번에 가져온다 (`@OrderBy("sortOrder ASC")` 유지).
     * 조건에 맞지 않으면 빈 Optional — 호출부에서 404로 변환한다 (SC-09).
     */
    @EntityGraph(attributePaths = "items")
    Optional<Quote> findWithItemsByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * 회사 스코프 없는 단건 조회 — {@code QuoteQuery.getPublicView} 전용이다.
     *
     * <p>고객 열람 링크는 회사에 로그인한 요청이 아니라 companyId를 들고 오지 못한다.
     * 대신 토큰이 이미 견적 하나를 특정하므로 범위가 그 한 건으로 좁혀져 있다 (docs/11 §7.2).
     * <b>구성원 요청을 직접 받는 경로에서는 쓰지 않는다</b> — 거기서는 위의 회사 스코프 버전을 쓴다.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Quote> findWithItemsById(UUID id);
}
