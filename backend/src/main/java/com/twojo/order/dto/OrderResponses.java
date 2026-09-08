package com.twojo.order.dto;

import com.twojo.order.entity.Order;
import com.twojo.order.entity.OrderItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 주문 응답 DTO. 금액·항목은 <b>전환 시점 스냅샷</b>이다 (OD-04·05) — 견적을 조인해 만들지 않는다.
 * 필드는 08의 {@code OrderResponse}·{@code OrderDetailResponse}를 따른다.
 */
public final class OrderResponses {

    private OrderResponses() {
    }

    /**
     * 주문이 스스로 답할 수 없는 값 — 견적·Deal·고객사에서 온다.
     *
     * <p>{@code orders}에는 {@code deal_id}도 {@code customer_id}도 없다 (ERD). 그래서 이 값들은
     * <b>quote → deal → customer</b>를 거쳐야 나오고, 그 경유는 전부 경계 인터페이스다
     * ({@code QuoteQuery} · {@code DealQuery} · {@code CustomerQuery}, 11 §7.3).
     *
     * <p>{@code dealStage}는 상세에만 실린다 — 주문 전환의 부수 효과인 <b>자동 성사(OD-06)를
     * 응답에서 바로 확인</b>하기 위한 값이다. 없으면 전환 직후 화면이 Deal을 다시 조회해야 한다.
     */
    public record Origin(String quoteNo, UUID dealId, String dealTitle, String dealStage,
                         UUID customerId, String customerName) {
    }

    /** 목록 (OD-08) — 항목을 담지 않는다. 목록 화면이 필요로 하는 것은 합계뿐이다 */
    public record OrderRow(
            UUID id, String orderNo,
            UUID quoteId, String quoteNo,
            UUID dealId, String dealTitle,
            UUID customerId, String customerName,
            Long supplyAmount, Long vatAmount, Long totalAmount,
            LocalDate startDate, LocalDate deliveryDate,
            Instant createdAt) {

        public static OrderRow of(Order order, Origin origin) {
            return new OrderRow(order.getId(), order.getOrderNo(),
                    order.getQuoteId(), origin.quoteNo(),
                    origin.dealId(), origin.dealTitle(),
                    origin.customerId(), origin.customerName(),
                    order.getSupplyAmount(), order.getVatAmount(), order.getTotalAmount(),
                    order.getStartDate(), order.getDeliveryDate(),
                    order.getCreatedAt());
        }
    }

    /**
     * 상세 (OD-09) — 스냅샷 항목 포함. 전환(OD-01)의 201 응답도 이 모양이다.
     *
     * @param dealStage 전환 직후에는 항상 {@code WON}이다 (OD-06) — 자동 성사의 확인용이다
     */
    public record OrderDetail(
            UUID id, String orderNo,
            UUID quoteId, String quoteNo,
            UUID dealId, String dealTitle, String dealStage,
            UUID customerId, String customerName,
            Long supplyAmount, Long vatAmount, Long totalAmount,
            List<Line> items,
            LocalDate startDate, LocalDate deliveryDate,
            Instant createdAt) {

        /** FK 없는 값 복사 (OD-04) — {@code productId}도 {@code sortOrder}도 없다 */
        public record Line(String name, String unit, int quantity, Long unitPrice, Long amount) {

            static Line of(OrderItem item) {
                return new Line(item.getName(), item.getUnit(),
                        item.getQuantity(), item.getUnitPrice(), item.getAmount());
            }
        }

        public static OrderDetail of(Order order, Origin origin) {
            return new OrderDetail(order.getId(), order.getOrderNo(),
                    order.getQuoteId(), origin.quoteNo(),
                    origin.dealId(), origin.dealTitle(), origin.dealStage(),
                    origin.customerId(), origin.customerName(),
                    order.getSupplyAmount(), order.getVatAmount(), order.getTotalAmount(),
                    order.getItems().stream().map(Line::of).toList(),
                    order.getStartDate(), order.getDeliveryDate(),
                    order.getCreatedAt());
        }
    }
}
