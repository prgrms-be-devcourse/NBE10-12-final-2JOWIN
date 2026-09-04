package com.twojo.quote.dto;

import com.twojo.quote.entity.Quote;
import com.twojo.quote.entity.QuoteItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 견적 응답 DTO. 금액 3분리는 <b>항상 서버 계산값</b>이다 (QT-08·22, 08 검증 노트 #1). */
public final class QuoteResponses {

    private QuoteResponses() {
    }

    /**
     * 목록 (QT-20) — 항목을 담지 않는다.
     * 견적마다 항목을 끌어오면 N+1이 되고, 목록 화면이 필요로 하는 것은 합계뿐이다.
     */
    public record QuoteItemRow(
            UUID id, String quoteNo, String status, String vatMode,
            Long supplyAmount, Long vatAmount, Long totalAmount,
            LocalDate validUntil, UUID dealId,
            Instant sentAt, Instant firstViewedAt, Instant respondedAt,
            Integer version, Instant createdAt) {

        public static QuoteItemRow of(Quote quote) {
            return new QuoteItemRow(quote.getId(), quote.getQuoteNo(),
                    quote.getStatus().name(), quote.getVatMode().name(),
                    quote.getSupplyAmount(), quote.getVatAmount(), quote.getTotalAmount(),
                    quote.getValidUntil(), quote.getDealId(),
                    quote.getSentAt(), quote.getFirstViewedAt(), quote.getRespondedAt(),
                    quote.getVersion(), quote.getCreatedAt());
        }
    }

    /** 상세 — 항목 포함. 정렬은 엔티티의 {@code @OrderBy("sortOrder ASC")}가 보장한다 (QT-07) */
    public record QuoteDetail(
            UUID id, String quoteNo, String status, String vatMode,
            Long supplyAmount, Long vatAmount, Long totalAmount,
            LocalDate validUntil, String terms, UUID dealId,
            UUID clonedFromQuoteId,
            Instant sentAt, Instant firstViewedAt, Instant respondedAt, String rejectReason,
            List<Line> items, Integer version, Instant createdAt) {

        /**
         * @param catalogPriceAtCreation 작성 시점 카탈로그 단가 (QT-24). 직접 입력이면 null.
         *                               <b>{@code unitPrice}와 다를 수 있다</b> — 담당자가 조정했다는 뜻이다 (QT-05)
         */
        public record Line(UUID id, UUID productId, String name, String unit,
                           int quantity, Long unitPrice, Long amount,
                           Long catalogPriceAtCreation, int sortOrder) {

            static Line of(QuoteItem item) {
                return new Line(item.getId(), item.getProductId(), item.getName(), item.getUnit(),
                        item.getQuantity(), item.getUnitPrice(), item.getAmount(),
                        item.getCatalogPriceAtCreation(), item.getSortOrder());
            }
        }

        public static QuoteDetail of(Quote quote) {
            return new QuoteDetail(quote.getId(), quote.getQuoteNo(),
                    quote.getStatus().name(), quote.getVatMode().name(),
                    quote.getSupplyAmount(), quote.getVatAmount(), quote.getTotalAmount(),
                    quote.getValidUntil(), quote.getTerms(), quote.getDealId(),
                    quote.getClonedFromQuoteId(),
                    quote.getSentAt(), quote.getFirstViewedAt(), quote.getRespondedAt(),
                    quote.getRejectReason(),
                    quote.getItems().stream().map(Line::of).toList(),
                    quote.getVersion(), quote.getCreatedAt());
        }
    }
}
