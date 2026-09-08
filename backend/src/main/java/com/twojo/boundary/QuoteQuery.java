package com.twojo.boundary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 견적 후보 조회 계약 — 구현: C(quote 모듈). D의 배치·대시보드가 소비한다 (v2.0.1 보강).
 * (docs/11-work-breakdown.md §4)
 */
public interface QuoteQuery {

    /** SENT·VIEWED — NT-05 리마인드 · DB-03 응답 대기 */
    List<QuoteSummary> findAwaitingResponse(UUID companyId);

    /** NT-06 임박 알림 후보 (valid_until 기준) */
    List<QuoteSummary> findExpiringUntil(LocalDate date);

    /**
     * 고객 열람 페이지 렌더용 (AP-02·07 · SC-08) — <b>quote 소유 데이터만</b> 준다.
     *
     * <p>C·D 합의 (2026-08-31). D가 조립해야 하는 것은 여기에 담지 않는다 —
     * C가 A의 데이터를 대신 조회해 넘기면 소유자별 책임이 흐려지기 때문이다.
     * <ul>
     *   <li>회사명·사업자번호 → D가 {@link CompanyQuery}로</li>
     *   <li>현재 담당자(AP-18, 발송자 스냅샷 아님) → D가 {@link DealQuery#assigneeIdOf}
     *       + {@link MemberQuery}로</li>
     * </ul>
     *
     * <p>{@link ViewTokenCommand#issue}도 이 메서드로 {@code validUntil}·{@code companyId}를
     * 얻는다 — 그래서 issue의 시그니처를 넓히지 않았다(PR #13 계약 유지).
     * <b>issue 시점의 status는 아직 {@code DRAFT}다</b> — C가 발급 성공 후에 SENT로 바꾸기
     * 때문이다(Q-40 순서 합의). issue 구현에서 SENT를 전제로 검증하면 항상 실패한다.
     *
     * <p>토큰 만료 시각(= validUntil 당일 23:59:59)으로의 변환은 D가 한다.
     *
     * <p><b>없으면 RESOURCE_NOT_FOUND를 던진다</b> — 호출 맥락이 존재를 보장하는 자리라
     * ({@code quote_view_token.quote_id} FK · issue는 C의 발송 트랜잭션 안) 없다는 것은
     * 데이터 이상이다. 호출자는 null을 검사하지 않는다.
     * 404 문구는 SC-09 통일 문구를 따르므로 고객 열람 경로에서도 존재가 노출되지 않는다.
     */
    PublicQuoteView getPublicView(UUID quoteId);

    /**
     * 견적 id 묶음 → 출처 배치 조회 — <b>주문 조회의 유일한 통로다</b> (OD-08·09).
     *
     * <p>{@code orders}에는 {@code deal_id} 컬럼이 없다 (ERD). 주문 응답의 {@code dealId}·
     * {@code quoteNo}는 견적을 거쳐야 나오는데, order 모듈은 quote 테이블을 직접 읽지 않는다
     * (11 §7.3). 줄마다 부르지 않게 배치로 받는다 — 목록 20건이면 조회도 20번이 된다.
     *
     * <p>반환은 요청 순서를 보장하지 않으므로 호출자가 id로 인덱싱한다.
     * 없는 id는 결과에서 빠진다(예외 아님). 빈 목록을 넘기면 빈 목록을 돌려준다.
     */
    List<QuoteOrigin> originsByIds(UUID companyId, Collection<UUID> quoteIds);

    /**
     * 담당 Deal 묶음에 속한 견적 id 전체 — 주문 목록의 <b>범위 필터</b>다 (SC-04, 09 §80).
     *
     * <p>주문의 범위도 {@code deal.assignee_member_id}에서 파생하는데(견적과 같은 축),
     * {@code orders}에서 Deal까지 가려면 quote를 거쳐야 한다. 그 조인을 모듈 밖에서 할 수 없어
     * <b>id 집합으로 받아 {@code quote_id IN (...)}으로 좁힌다</b>.
     *
     * <p><b>{@code scope == OWNED_ONLY}일 때만 호출한다</b> — 기업 관리자는 회사 범위면 충분하다
     * ({@code DealQuery.assignedDealIds}와 같은 규약). 빈 목록을 넘기면 빈 목록을 돌려준다 —
     * 담당 Deal이 하나도 없는 영업이고, 그에게는 주문도 하나도 보이지 않아야 한다.
     */
    List<UUID> quoteIdsByDeals(UUID companyId, Collection<UUID> dealIds);

    /** firstViewedAt이 null이면 미열람 (v2.0.2, GAP-08) */
    record QuoteSummary(UUID id, String quoteNo, String customerName,
                        Instant sentAt, Instant firstViewedAt, LocalDate validUntil) {}

    /**
     * 열람 페이지 렌더 데이터 — 금액 3분리는 항상 서버 계산값이다 (QT-08·22·25).
     *
     * @param dealId    D가 현재 담당자를 조회하는 축 (AP-18)
     * @param companyId D가 회사 정체성·정지 여부(SC-10)·알림 행 생성에 쓰는 축
     */
    record PublicQuoteView(UUID quoteId, String quoteNo, String status,
                           String vatMode, String terms, LocalDate validUntil,
                           Long supplyAmount, Long vatAmount, Long totalAmount,
                           List<Item> items,
                           UUID dealId, UUID companyId) {

        /**
         * 발송 시점 값 복사본 — product 조인이 아니다 (QT-24, PR-04 무영향).
         *
         * <p><b>{@code public}이 필요하다.</b> 인터페이스에 중첩된 {@code PublicQuoteView}는
         * 암묵적으로 public이지만, <b>record 안에 중첩된 이 타입은 그렇지 않다</b> —
         * 기본 접근이라 {@code com.twojo.boundary} 밖에서는 이름조차 쓸 수 없었다.
         * 항목 없는 뷰만 만들어 보면(빈 리스트) 드러나지 않는다.
         */
        public record Item(String name, String unit, int quantity,
                    Long unitPrice, Long amount, int sortOrder) {}
    }

    /**
     * 주문이 견적에서 물려받는 최소 정보 — 표시용 {@code quoteNo}와 범위 축인 {@code dealId}.
     *
     * <p>금액·항목은 여기 없다. 주문은 전환 시점 값을 <b>자기 테이블에 복사해 가지고</b>
     * 있어서(OD-04·05) 조회 때 견적을 다시 볼 이유가 없다 — 다시 보면 스냅샷이 무너진다.
     */
    record QuoteOrigin(UUID quoteId, String quoteNo, UUID dealId) {}
}
