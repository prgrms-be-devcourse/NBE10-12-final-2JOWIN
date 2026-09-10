package com.twojo.deal.dto;

import com.twojo.boundary.OrderQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.deal.entity.Deal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Deal 응답 DTO (docs/08-dto.md §C). */
public final class DealResponses {

    private DealResponses() {
    }

    /**
     * 목록·보드 공용 (DL-06·13·14).
     *
     * @param wonAmount 성사 후 주문 합계 (DL-18) — <b>주문 전환 이슈까지 null</b>.
     *                  표시 규칙은 성사 전 expectedAmount, 성사 후 wonAmount
     */
    public record DealItem(
            UUID id, String title, String stage,
            Long expectedAmount, Long wonAmount,
            UUID customerId, String customerName,
            UUID assigneeMemberId, String assigneeMemberName,
            LocalDate dueDate, Integer version, Instant createdAt) {

        /**
         * @param wonAmount 성사 딜의 주문 합계 (DL-18). 진행 중이면 {@code null}이다 —
         *                  표시 규칙이 "성사 전 expectedAmount, 성사 후 wonAmount"라
         *                  0을 넣으면 화면이 "주문 0원"으로 읽는다 (08 §C)
         */
        public static DealItem of(Deal deal, Long wonAmount, String customerName, String assigneeMemberName) {
            return new DealItem(deal.getId(), deal.getTitle(), deal.getStage().name(),
                    deal.getExpectedAmount(), wonAmount,
                    deal.getCustomerId(), customerName,
                    deal.getAssigneeMemberId(), assigneeMemberName,
                    deal.getDueDate(), deal.getVersion(), deal.getCreatedAt());
        }
    }

    /**
     * 상세 (DL-15·18) — 견적·주문은 <b>요약 목록만</b> 담고 활동 이력 전체는 담지 않는다.
     * 타임라인은 {@code /deals/{dealId}/activities}가 담당한다 (07 §C, v1.6.3).
     *
     * <p>{@code quotes}·{@code orders}는 경계 창구({@code QuoteQuery.briefsByDeals} ·
     * {@code OrderQuery.briefsByQuotes})로 채운다. {@code wonAmount}는 그 주문 줄의 합이고
     * <b>성사 전에는 null</b>이다 — 화면 규칙이 "성사 전 expectedAmount, 성사 후 wonAmount"라
     * 진행 중인 딜에 0을 넣으면 "주문이 0원"으로 읽힌다 (#304).
     */
    public record DealDetail(
            UUID id, String title, String stage,
            Long expectedAmount, Long wonAmount,
            UUID customerId, String customerName,
            UUID assigneeMemberId, String assigneeMemberName,
            LocalDate dueDate, String lostReason,
            List<QuoteSummary> quotes,
            List<OrderSummary> orders,
            Integer version, Instant createdAt) {

        public record QuoteSummary(UUID id, String quoteNo, String status,
                                   Long totalAmount, Instant sentAt) {}

        public record OrderSummary(UUID id, String orderNo, Long totalAmount, Instant createdAt) {}

        public static DealDetail of(Deal deal, String customerName, String assigneeMemberName,
                                   List<QuoteQuery.QuoteBrief> quoteBriefs,
                                   List<OrderQuery.OrderBrief> orderBriefs,
                                   Long wonAmount) {
            return new DealDetail(deal.getId(), deal.getTitle(), deal.getStage().name(),
                    deal.getExpectedAmount(), wonAmount,
                    deal.getCustomerId(), customerName,
                    deal.getAssigneeMemberId(), assigneeMemberName,
                    deal.getDueDate(), deal.getLostReason(),
                    quoteBriefs.stream()
                            .map(q -> new QuoteSummary(q.id(), q.quoteNo(), q.status(),
                                    q.totalAmount(), q.sentAt()))
                            .toList(),
                    orderBriefs.stream()
                            .map(o -> new OrderSummary(o.id(), o.orderNo(), o.totalAmount(), o.createdAt()))
                            .toList(),
                    deal.getVersion(), deal.getCreatedAt());
        }
    }
}
