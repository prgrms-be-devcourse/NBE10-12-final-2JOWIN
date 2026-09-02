package com.twojo.deal.dto;

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

        public static DealItem of(Deal deal, String customerName, String assigneeMemberName) {
            return new DealItem(deal.getId(), deal.getTitle(), deal.getStage().name(),
                    deal.getExpectedAmount(), null,
                    deal.getCustomerId(), customerName,
                    deal.getAssigneeMemberId(), assigneeMemberName,
                    deal.getDueDate(), deal.getVersion(), deal.getCreatedAt());
        }
    }

    /**
     * 상세 (DL-15·18) — 견적·주문은 <b>요약 목록만</b> 담고 활동 이력 전체는 담지 않는다.
     * 타임라인은 {@code /deals/{dealId}/activities}가 담당한다 (07 §C, v1.6.3).
     *
     * <p>{@code quotes}·{@code orders}는 quote·order 모듈 조회 창구가 정해질 때까지 빈 목록이다
     * (이슈 본문 「리뷰 필요」 참조).
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

        public static DealDetail of(Deal deal, String customerName, String assigneeMemberName) {
            return new DealDetail(deal.getId(), deal.getTitle(), deal.getStage().name(),
                    deal.getExpectedAmount(), null,
                    deal.getCustomerId(), customerName,
                    deal.getAssigneeMemberId(), assigneeMemberName,
                    deal.getDueDate(), deal.getLostReason(),
                    List.of(), List.of(),
                    deal.getVersion(), deal.getCreatedAt());
        }
    }
}
