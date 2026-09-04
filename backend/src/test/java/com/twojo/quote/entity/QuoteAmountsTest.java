package com.twojo.quote.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.quote.entity.Quote.VatMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 견적 금액 3분리 계산 (QT-08·22·23·25) — 반올림 규칙(Q-45)과 vat_mode 무관성(Q-46).
 * <p>JPA 없이 도는 순수 단위 테스트다. 계산이 엔티티 밖 record에 있는 이유이기도 하다.
 */
class QuoteAmountsTest {

    @Test
    @DisplayName("항목 합계가 공급가액이 되고 부가세 10%가 더해진다")
    void 공급가액_기준_계산() {
        QuoteAmounts amounts = QuoteAmounts.of(1_000_000L);

        assertThat(amounts.supplyAmount()).isEqualTo(1_000_000L);
        assertThat(amounts.vatAmount()).isEqualTo(100_000L);
        assertThat(amounts.totalAmount()).isEqualTo(1_100_000L);
    }

    @ParameterizedTest(name = "공급가액 {0}원 → 부가세 {1}원 · 합계 {2}원")
    @CsvSource({
            "1,      0,   1",        // 0.1원 → 0원 (내림)
            "4,      0,   4",        // 0.4원 → 0원
            "5,      1,   6",        // 0.5원 → 1원 (HALF_UP 경계)
            "10,     1,   11",       // 1.0원 → 1원 (단수 없음)
            "999,    100, 1099",     // 99.9원 → 100원
            "1005,   101, 1106",     // 100.5원 → 101원 (경계)
            "12345,  1235, 13580",   // 1234.5원 → 1235원 (경계)
            "0,      0,   0"         // 0원 견적 — 단가 0원 하한 (Q-02)
    })
    @DisplayName("원 미만 단수는 반올림한다 (Q-45)")
    void 원_단위_반올림(long supply, long expectedVat, long expectedTotal) {
        QuoteAmounts amounts = QuoteAmounts.of(supply);

        assertThat(amounts.vatAmount()).isEqualTo(expectedVat);
        assertThat(amounts.totalAmount()).isEqualTo(expectedTotal);
    }

    /**
     * Q-46의 핵심 — <b>모드를 바꿔도 금액이 같다</b>.
     *
     * <p>{@code QuoteAmounts.of}가 아예 {@code vatMode}를 받지 않으므로 계산 단계에서는
     * 자명하지만, 여기서 고정하는 것은 <b>엔티티까지 포함한 결론</b>이다 —
     * {@code Quote.changeVatMode()}가 재계산을 부르지 않는다는 것과 짝이다.
     * 누군가 모드별 분기를 되살리면 이 테스트가 먼저 깨진다.
     */
    @ParameterizedTest(name = "{0} 모드")
    @EnumSource(VatMode.class)
    @DisplayName("vat_mode는 금액에 영향을 주지 않는다 — 표시 기준일 뿐이다 (Q-46)")
    void 모드는_금액을_바꾸지_않는다(VatMode mode) {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID(), "Q-2609-001", LocalDate.now().plusDays(30));
        quote.replaceItems(List.of(
                QuoteItem.of(null, "사무용 의자", "개", 3, 150_000L, null, 0)));
        QuoteAmounts before = new QuoteAmounts(
                quote.getSupplyAmount(), quote.getVatAmount(), quote.getTotalAmount());

        quote.changeVatMode(mode);

        assertThat(quote.getSupplyAmount()).isEqualTo(before.supplyAmount());
        assertThat(quote.getVatAmount()).isEqualTo(before.vatAmount());
        assertThat(quote.getTotalAmount()).isEqualTo(before.totalAmount());
        assertThat(quote.getVatMode()).isEqualTo(mode);   // 플래그는 바뀐다
    }

    @ParameterizedTest(name = "항목 합계 {0}원")
    @CsvSource({"1", "5", "7", "99", "999", "1005", "10000", "12345", "999999", "1234567"})
    @DisplayName("어떤 금액에서도 공급가액 + 부가세 = 합계가 성립한다 (QT-25)")
    void 금액_3분리_정합(long itemsTotal) {
        QuoteAmounts amounts = QuoteAmounts.of(itemsTotal);

        assertThat(amounts.supplyAmount() + amounts.vatAmount())
                .as("항목 합계 %d원", itemsTotal)
                .isEqualTo(amounts.totalAmount());
    }

    @Test
    @DisplayName("음수 항목 합계는 거부한다 (단가 0원 하한, Q-02)")
    void 음수_거부() {
        assertThatThrownBy(() -> QuoteAmounts.of(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
