package com.twojo.quote.repository;

import com.twojo.quote.entity.Quote;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * 주문 전환 전용 — <b>{@code SELECT ... FOR UPDATE}로 견적 행을 잡는다</b> (OD-03).
     *
     * <p>락은 호출자가 커밋할 때까지 유지되어, 같은 견적의 동시 전환이 여기서 줄을 선다.
     * 이 락이 없으면 "이미 주문이 있는가" 검사를 여럿이 동시에 통과하고,
     * {@code orders.quote_id UNIQUE}가 최종 방어선으로 터지면서 <b>409가 아니라 500</b>이 된다.
     *
     * <p><b>항목을 함께 fetch하지 않는다.</b> PostgreSQL은 outer join의 nullable 쪽에
     * {@code FOR UPDATE}를 걸지 못해 조인 fetch와 같이 쓸 수 없다 — 항목은 뒤이은
     * 지연 로딩으로 따라온다(같은 트랜잭션이라 스냅샷은 락 이후 값이다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :quoteId and q.companyId = :companyId")
    Optional<Quote> findForUpdate(UUID quoteId, UUID companyId);

    /** {@code QuoteQuery.originsByIds} — 주문 목록·상세에 붙일 견적 출처 배치 조회 (OD-08·09) */
    List<Quote> findByCompanyIdAndIdIn(UUID companyId, Collection<UUID> ids);

    /**
     * {@code QuoteQuery.findAwaitingResponse} — 응답 대기(SENT·VIEWED) 견적 (NT-05, DB-03).
     *
     * <p>회사 스코프만 건다 — <b>담당 축은 거르지 않는다.</b> 배치에는 {@code AccessContext}가 없고,
     * 대시보드는 {@code QuoteSummary.dealId}로 호출자가 직접 거른다 (계약 javadoc).
     *
     * <p>발송이 오래된 것부터 준다 — 리마인드는 가장 오래 답이 없는 건이 먼저다.
     */
    List<Quote> findByCompanyIdAndStatusInOrderBySentAtAsc(UUID companyId, Collection<Quote.Status> statuses);

    /**
     * {@code QuoteQuery.findExpiringBetween} — 유효기간이 구간 안에 든 응답 대기 견적 (NT-06).
     *
     * <p><b>회사 스코프를 걸지 않는다</b> — 계약이 전 회사를 한 번에 돌려주고, 호출자가
     * {@code QuoteSummary.companyId}로 그룹핑해 회사별 정지 판정(Q-27)을 한다 (계약 javadoc).
     * NT-05가 회사별로 도는 것과 갈리는 이유는 각 배치가 자기 쿼리 모양에 맞췄기 때문이다.
     *
     * <p>{@code Between}은 <b>양 끝을 포함</b>한다 — 계약의 "하한 포함 · 상한 포함"과 같다.
     * 하한이 있어야 이미 만료된 견적에 "임박했습니다"가 나가지 않는다.
     *
     * <p>유효기간 오름차순 — 다만 이 순서는 <b>회사 안에서만</b> 살아남는다. {@code QuoteQueryImpl}이
     * 회사별로 나눠 이름을 채우면서 회사 간 순서는 버리고, 계약도 전역 정렬을 약속하지 않는다.
     */
    List<Quote> findByStatusInAndValidUntilBetweenOrderByValidUntilAsc(
            Collection<Quote.Status> statuses, LocalDate from, LocalDate to);

    /**
     * 기간 만료 배치(Q-37) — 유효기간이 지난 응답 대기 견적. <b>id만 읽는다.</b>
     *
     * <p>회사 스코프를 걸지 않는다 — 만료는 알림이 아니라 <b>상태 전이</b>라
     * 정지 회사(Q-27)도 그대로 닫혀야 한다 ({@code ViewTokenCommand.expire} javadoc:
     * "회사 정지 중에도 만료 전이는 계속 돈다 — 알림 억제 판정은 D가 한다").
     *
     * <p>{@code Before}는 <b>상한을 포함하지 않는다</b> — 오늘 자정까지는 아직 유효하기
     * 때문이다. 유효기간이 오늘인 견적은 내일 배치에서 닫힌다 (링크 만료도
     * {@code valid_until 23:59:59}라 같은 경계다, Q-17).
     *
     * <p>엔티티가 아니라 id를 읽는 이유는 <b>워커가 견적마다 새 트랜잭션을 열기 때문</b>이다 —
     * 배치 트랜잭션에서 읽은 엔티티를 넘기면 그 영속성 컨텍스트 밖에서 만지게 된다.
     */
    @Query("select q.id from Quote q where q.status in :statuses and q.validUntil < :today")
    List<UUID> findIdsExpiredBefore(Collection<Quote.Status> statuses, LocalDate today);

    /**
     * 딜에 걸린 진행 중 견적 (DL-10) — 딜 실패 시 닫을 대상이다.
     *
     * <p>엔티티로 읽는다. {@code Quote.expire()}가 상태 판정과 전이를 함께 하고, 더티 체킹으로
     * 저장된다 — JPQL 일괄 update는 그 판정을 여기로 복제하게 되고 {@code @Version}도 건너뛴다.
     *
     * <p><b>회사 조건이 없다.</b> 호출자가 회사 안에서 얻은 dealId를 넘기는 자리이고,
     * {@code fk_quote_deal}이 남의 회사 딜을 가리키는 견적을 막는다 (계약 javadoc).
     */
    List<Quote> findByDealIdAndStatusIn(UUID dealId, Collection<Quote.Status> statuses);

    /** {@code QuoteQuery.quoteIdsByDeals} — 주문 목록의 SC-04 범위 필터. id만 읽는다 */
    @Query("select q.id from Quote q where q.companyId = :companyId and q.dealId in :dealIds")
    List<UUID> findIdsByDeals(UUID companyId, Collection<UUID> dealIds);

    /**
     * {@code QuoteQuery.briefsByDeals} — 딜 상세의 견적 목록 (DL-15).
     *
     * <p>{@code findIdsByDeals}와 달리 화면에 그릴 필드가 필요해 엔티티를 읽는다.
     * 상태로 거르지 않는다 — 회수·반려도 그 딜의 이력이다.
     *
     * <p><b>최근 것부터 준다</b> — 딜 상세의 견적 탭은 "지금 무엇이 오가는가"를 보는 자리라
     * 마지막에 만든 견적이 위에 와야 한다. 복제(QT-19)로 재제안한 건이 원본 아래 묻히면
     * 담당자가 옛 견적을 본다. 프론트 목도 같은 순서다.
     */
    List<Quote> findByCompanyIdAndDealIdInOrderByCreatedAtDesc(UUID companyId, Collection<UUID> dealIds);
}
