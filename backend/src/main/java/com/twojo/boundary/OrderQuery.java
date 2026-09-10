package com.twojo.boundary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 주문 집계 창구 — 구현: C(order). 대시보드 집계(C, deal)가 소비한다 (DB-02·06, 11 §7.2).
 *
 * <p><b>왜 필요한가</b> — 성사 금액은 예상 금액이 아니라 <b>주문 합계</b>다 (DL-18).
 * 그런데 집계를 맡은 {@code SalesStatsQueryImpl}은 deal 모듈이라 {@code orders}를 직접 읽을 수 없다
 * (11 §7.3). 같은 사람이 소유한 두 모듈이라도 경계는 같다.
 *
 * <p><b>범위를 여기서 판정하지 않는다.</b> 주문에는 담당자 컬럼도 {@code deal_id}도 없고
 * 범위 축은 {@code deal.assignee_member_id} 하나뿐이라(SC-04, 09 §80), 호출자가
 * {@code assignedDealIds} → {@code QuoteQuery.quoteIdsByDeals}로 좁혀 견적 id로 넘긴다.
 * {@code OrderSpecs}가 목록(OD-08)에서 하는 일과 <b>같은 규약</b>이다.
 */
public interface OrderQuery {

    /**
     * 기간 안에 전환된 주문 (DB-02·06) — <b>전환 시각은 {@code created_at}</b>이다.
     *
     * <p>{@code orders}에는 별도의 전환 시각 컬럼이 없다. 목록의 기간 필터(OD-08)도 같은 축을 쓴다.
     *
     * <p><b>견적 하나에 주문은 최대 하나다</b> — {@code orders.quote_id}가 UNIQUE이고(OD-03 최종 방어)
     * 재전환은 {@code QUOTE_ALREADY_CONVERTED}로 막힌다. 그래서 견적당 <b>한 행</b>이고,
     * 전환 건수는 곧 <b>행 수</b>다. 딜 하나에 주문이 여럿일 수는 있다 — 승인 견적이 여럿이면 그렇다 (Q-25).
     *
     * <p><b>기간은 한국 날짜로 받는다.</b> 시각이 아니라 날짜인 이유는 경계 계산을 이 모듈이
     * 소유하기 때문이다 — {@code created_at}이 여기 컬럼이고, 호출자가 KST로 끊어 넘기면
     * 같은 규칙이 모듈마다 한 벌씩 생긴다. 목록(OD-08)의 기간 필터와 <b>같은 축·같은 규칙</b>이다.
     *
     * @param quoteIds <b>null이면 제한 없음</b>(기업 관리자, SC-05).
     *                 <b>빈 목록이면 아무것도 없다</b> — 담당 딜이 하나도 없는 영업이다.
     *                 "전부"와 "아무것도"를 뒤집으면 SC-04가 통째로 뚫린다
     * @param from     전환일 하한(포함). <b>null이면 하한 없음</b>
     * @param to       전환일 상한(<b>포함</b>) — 그날 23:59:59까지다. <b>null이면 상한 없음</b>.
     *                 둘 다 null이면 전 기간이고, {@code DealSummary.wonAmount}(DL-18)처럼
     *                 기간을 묻지 않는 소비자가 같은 창구를 쓸 수 있다
     */
    List<QuoteWonTotal> wonTotalsByQuotes(UUID companyId, Collection<UUID> quoteIds,
                                          LocalDate from, LocalDate to);

    /**
     * 견적 하나가 만든 주문의 금액.
     *
     * <p>금액은 {@code total_amount}(VAT 포함)다 — 파이프라인의 예상 금액과 축을 맞춘다
     * (2026-09-10 D 확정). 공급가로 바꾸면 "예상 → 성사" 전환이 화면에서 어긋난다.
     *
     * <p>{@code quoteId}를 함께 싣는 이유는 <b>호출자가 담당자를 되짚기 위해서</b>다 —
     * {@code QuoteQuery.originsByIds}로 딜을 얻고, 딜에서 담당자가 나온다 (DB-06).
     */
    /**
     * 딜 상세의 주문 요약 목록 (DL-15) — 견적 묶음이 만든 주문을 한 줄씩.
     *
     * <p><b>{@link #wonTotalsByQuotes}와 갈리는 이유는 소비처다.</b> 그쪽은 집계라 금액만
     * 있으면 되지만, 딜 상세는 화면에 주문번호와 전환 시각을 그린다.
     *
     * <p>범위 판정은 여기서 하지 않는다 — 주문에는 담당자 컬럼이 없어 호출자가
     * {@code QuoteQuery.quoteIdsByDeals}로 좁혀 넘긴다 ({@code wonTotalsByQuotes}와 같은 규약).
     * <b>빈 묶음이면 빈 목록</b>이다 — 여기서는 "제한 없음"(null)을 받지 않는다.
     * 딜 상세는 언제나 특정 딜의 견적으로 좁혀진 자리라 전 회사를 물을 일이 없고,
     * null을 허용하면 실수로 회사 전체 주문이 한 딜에 붙는다.
     *
     * <p><b>최근 전환부터 준다</b> — 견적 요약과 같은 순서다. 여러 견적을 한 번에 물으면
     * 견적이 섞인 채 시간순이므로, 호출자가 {@code quoteId}로 묶으면 그 안에서 순서가 유지된다.
     */
    List<OrderBrief> briefsByQuotes(UUID companyId, Collection<UUID> quoteIds);

    record QuoteWonTotal(UUID quoteId, long totalAmount) {}

    /**
     * 딜 상세에 그리는 주문 한 줄 (DL-15).
     *
     * <p>{@code quoteId}를 함께 싣는 이유는 <b>호출자가 딜로 되짚기 위해서</b>다 —
     * 주문에는 {@code deal_id}가 없고 견적을 지나야 딜에 닿는다.
     *
     * <p>{@code createdAt}이 곧 전환 시각이다 — {@code orders}에 별도 컬럼이 없다
     * ({@code wonTotalsByQuotes}의 기간 축과 같다).
     */
    record OrderBrief(UUID id, UUID quoteId, String orderNo, long totalAmount, Instant createdAt) {}
}
