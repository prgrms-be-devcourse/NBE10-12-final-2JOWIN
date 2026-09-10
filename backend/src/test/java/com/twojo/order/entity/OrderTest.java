package com.twojo.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.boundary.QuoteCommand.ConversionSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 주문 엔티티 — <b>스냅샷</b>(OD-04·05)과 <b>일정 기록</b>(OD-10)을 고정한다.
 *
 * <p>주문에는 상태가 없다 (전이표 §8, Q-09). 그래서 다른 엔티티 테스트와 달리
 * "이 상태에서 이 동작이 막히는가"를 볼 것이 없고, 대신 <b>값이 제대로 옮겨 적혔는가</b>가
 * 검증의 전부다 — 주문은 그 시점의 합의를 고정하는 문서이기 때문이다.
 */
class OrderTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();

    private static ConversionSnapshot snapshot() {
        return new ConversionSnapshot(QUOTE_ID, "Q-2609-001", UUID.randomUUID(),
                1_000_000L, 100_000L, 1_100_000L,
                List.of(new ConversionSnapshot.Line("사무용 의자", "EA", 10, 80_000L, 800_000L, 0),
                        new ConversionSnapshot.Line("책상", "EA", 2, 100_000L, 200_000L, 1)));
    }

    @Nested
    @DisplayName("전환 스냅샷 (OD-04·05)")
    class From {

        @Test
        @DisplayName("금액 3분리와 견적 id를 그대로 옮겨 적는다 — 다시 계산하지 않는다")
        void 금액_복사() {
            Order order = Order.from(COMPANY_ID, snapshot(), "O-2609-001");

            assertThat(order.getCompanyId()).isEqualTo(COMPANY_ID);
            assertThat(order.getQuoteId()).isEqualTo(QUOTE_ID);
            assertThat(order.getOrderNo()).isEqualTo("O-2609-001");
            assertThat(order.getSupplyAmount()).isEqualTo(1_000_000L);
            assertThat(order.getVatAmount()).isEqualTo(100_000L);
            assertThat(order.getTotalAmount()).isEqualTo(1_100_000L);
        }

        @Test
        @DisplayName("항목이 값으로 복사되고 주문에 연결된다 — order_id가 NOT NULL이라 저장 전에 서야 한다")
        void 항목_복사() {
            Order order = Order.from(COMPANY_ID, snapshot(), "O-2609-001");

            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getItems()).allSatisfy(item ->
                    assertThat(item.getOrder()).isSameAs(order));

            OrderItem first = order.getItems().getFirst();
            assertThat(first.getName()).isEqualTo("사무용 의자");
            assertThat(first.getUnit()).isEqualTo("EA");
            assertThat(first.getQuantity()).isEqualTo(10);
            assertThat(first.getUnitPrice()).isEqualTo(80_000L);
            assertThat(first.getAmount()).isEqualTo(800_000L);
        }

        /**
         * 순서도 값 복사다 (V301). FK가 없어 <b>견적에서 받아 오는 것 말고는 순서를 알 방법이 없고</b>,
         * 여기서 빠지면 컬럼만 생기고 값이 안 채워져 재조회 순서가 그대로 갈린다.
         *
         * <p>정렬 자체가 실제로 도는지는 DB를 왕복해야 보이므로 {@code OrderItemOrderIntegrationTest}가 맡는다 —
         * 이 테스트는 메모리상 생성만 보기 때문에 삽입 순서와 구별되지 않는다.
         */
        @Test
        @DisplayName("항목 순서(sortOrder)도 함께 복사된다 — 견적 말고는 순서를 알 방법이 없다")
        void 순서_복사() {
            Order order = Order.from(COMPANY_ID, snapshot(), "O-2609-001");

            assertThat(order.getItems()).extracting(OrderItem::getSortOrder)
                    .containsExactly(0, 1);
        }

        /**
         * <b>스냅샷의 실증</b>이다 (OD-05). 항목 금액의 합(1,000,000)과 견적이 준
         * 공급가액이 우연히 같아 보이는 상황을 피하려고, 여기서는 <b>일부러 어긋난 값</b>을 넣는다.
         * 엔티티가 합계를 다시 계산한다면 이 단언이 깨진다.
         */
        @Test
        @DisplayName("항목 합계로 금액을 다시 세지 않는다 — 견적이 확정한 값을 그대로 적는다")
        void 합계를_다시_세지_않는다() {
            ConversionSnapshot 어긋난 = new ConversionSnapshot(QUOTE_ID, "Q-2609-001", UUID.randomUUID(),
                    999L, 99L, 1_098L,
                    List.of(new ConversionSnapshot.Line("사무용 의자", "EA", 10, 80_000L, 800_000L, 0)));

            Order order = Order.from(COMPANY_ID, 어긋난, "O-2609-001");

            assertThat(order.getSupplyAmount()).isEqualTo(999L);
            assertThat(order.getTotalAmount()).isEqualTo(1_098L);
            assertThat(order.getItems().getFirst().getAmount()).isEqualTo(800_000L);
        }

        @Test
        @DisplayName("착수일·납기는 비어 있다 — 전환 시점에 정해지지 않은 값이다 (OD-10)")
        void 일정은_비어_있다() {
            Order order = Order.from(COMPANY_ID, snapshot(), "O-2609-001");

            assertThat(order.getStartDate()).isNull();
            assertThat(order.getDeliveryDate()).isNull();
        }

        @Test
        @DisplayName("번호 없이 만들 수 없다 — order_no가 NOT NULL이라 저장 시점에야 터진다")
        void 번호는_필수() {
            assertThatThrownBy(() -> Order.from(COMPANY_ID, snapshot(), " "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Order.from(COMPANY_ID, snapshot(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("착수일·납기 기록 (OD-10)")
    class UpdateSchedule {

        private final LocalDate 착수 = LocalDate.of(2026, 9, 15);
        private final LocalDate 납기 = LocalDate.of(2026, 10, 15);

        @Test
        @DisplayName("두 날짜가 기록되고, 다시 부르면 덮인다 — 상태가 없어 몇 번이든 고칠 수 있다")
        void 기록과_수정() {
            Order order = Order.from(COMPANY_ID, snapshot(), "O-2609-001");

            order.updateSchedule(착수, 납기);
            assertThat(order.getStartDate()).isEqualTo(착수);
            assertThat(order.getDeliveryDate()).isEqualTo(납기);

            order.updateSchedule(착수.plusDays(1), 납기.plusDays(1));
            assertThat(order.getStartDate()).isEqualTo(착수.plusDays(1));
            assertThat(order.getDeliveryDate()).isEqualTo(납기.plusDays(1));
        }

        /**
         * 주문에는 다른 수정 경로가 없다 — 취소도 없다 (OD-11·12 제외, Q-09).
         * null이 "미변경"이면 잘못 적은 날짜를 되돌릴 방법이 아예 사라진다.
         */
        @Test
        @DisplayName("null은 미변경이 아니라 지움이다 — 잘못 적은 날짜를 되돌릴 유일한 경로다")
        void null은_지움() {
            Order order = Order.from(COMPANY_ID, snapshot(), "O-2609-001");
            order.updateSchedule(착수, 납기);

            order.updateSchedule(null, null);

            assertThat(order.getStartDate()).isNull();
            assertThat(order.getDeliveryDate()).isNull();
        }
    }
}
