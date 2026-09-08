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

    /**
     * 발송 결과 (08 `SendQuoteResponse`).
     *
     * @param dealStage Q-25 자동 승급이 <b>반영된</b> 값 — 화면이 딜 단계를 다시 묻지 않아도 된다
     * @param version   Q-38 — 발송 직후 회수로 이어지는 자리라 최신 version이 필요하다
     */
    public record SendResult(UUID quoteId, String status, String dealStage, Integer version) {
    }

    /**
     * 상세 — 항목 포함. 정렬은 엔티티의 {@code @OrderBy("sortOrder ASC")}가 보장한다 (QT-07).
     * 필드는 08의 {@code QuoteDetailResponse}를 따른다.
     *
     * @param supersededByQuoteId QT-28 대체 견적 — <b>지금은 항상 null이다.</b>
     *                            전용 컬럼이 없고 {@code cloned_from_quote_id}의 역방향으로 구하는
     *                            값인데(06 ERD: "복제 계보 · QT-28 대체 이동"), 복제(QT-19)가 아직
     *                            없어 그 관계를 가진 견적이 존재할 수 없다. <b>키는 지금 내보낸다</b> —
     *                            나중에 생기면 프론트가 필드 추가를 기다리지 않아도 된다
     */
    public record QuoteDetail(
            UUID id, String quoteNo, String status, String vatMode,
            Long supplyAmount, Long vatAmount, Long totalAmount,
            LocalDate validUntil, String terms,
            UUID dealId, String dealTitle,
            UUID clonedFromQuoteId, UUID supersededByQuoteId,
            Instant sentAt, Instant firstViewedAt, Instant respondedAt, String rejectReason,
            String responderName, String responderTitle,
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

        /** {@code dealTitle}은 범위 판정에서 이미 조회한 Deal 요약에서 온다 — 추가 조회가 없다 */
        public static QuoteDetail of(Quote quote, String dealTitle) {
            return new QuoteDetail(quote.getId(), quote.getQuoteNo(),
                    quote.getStatus().name(), quote.getVatMode().name(),
                    quote.getSupplyAmount(), quote.getVatAmount(), quote.getTotalAmount(),
                    quote.getValidUntil(), quote.getTerms(),
                    quote.getDealId(), dealTitle,
                    quote.getClonedFromQuoteId(), null,
                    quote.getSentAt(), quote.getFirstViewedAt(), quote.getRespondedAt(),
                    quote.getRejectReason(),
                    quote.getResponderName(), quote.getResponderTitle(),
                    quote.getItems().stream().map(Line::of).toList(),
                    quote.getVersion(), quote.getCreatedAt());
        }
    }
}
