package com.twojo.quote.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.quote.entity.Quote.VatMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 견적 금액 3분리 계산 (QT-08·22·23·25) — 반올림 규칙 검증.
 * <p>JPA 없이 도는 순수 단위 테스트다. 계산이 엔티티 밖 record에 있는 이유이기도 하다.
 */
class QuoteAmountsTest {

    @Nested
    @DisplayName("부가세 별도 (EXCLUDED, Q-16 기본)")
    class Excluded {

        @Test
        @DisplayName("항목 합계가 공급가액이 되고 부가세 10%가 더해진다")
        void 공급가액_기준_계산() {
            QuoteAmounts amounts = QuoteAmounts.of(1_000_000L, VatMode.EXCLUDED);

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
        void 원_단위_반올림(long supply, long expectedVat, long expectedTotal) {
            QuoteAmounts amounts = QuoteAmounts.of(supply, VatMode.EXCLUDED);

            assertThat(amounts.vatAmount()).isEqualTo(expectedVat);
            assertThat(amounts.totalAmount()).isEqualTo(expectedTotal);
        }
    }

    @Nested
    @DisplayName("부가세 포함 (INCLUDED, QT-23)")
    class Included {

        @Test
        @DisplayName("항목 합계가 합계금액이 되고 공급가액을 역산한다")
        void 합계_기준_역산() {
            QuoteAmounts amounts = QuoteAmounts.of(1_100_000L, VatMode.INCLUDED);

            assertThat(amounts.supplyAmount()).isEqualTo(1_000_000L);
            assertThat(amounts.vatAmount()).isEqualTo(100_000L);
            assertThat(amounts.totalAmount()).isEqualTo(1_100_000L);
        }

        @ParameterizedTest(name = "합계 {0}원 → 공급가액 {1}원 · 부가세 {2}원")
        @CsvSource({
                "1,      1,     0",       // 0.909…원 → 1원, 부가세는 차액 0원
                "10,     9,     1",       // 9.09…원 → 9원
                "11,     10,    1",       // 10.0원 → 10원 (단수 없음)
                "105,    95,    10",      // 95.45…원 → 95원
                "999,    908,   91",      // 908.18…원 → 908원
                "10000,  9091,  909",     // 9090.909…원 → 9091원 (홀수 금액)
                "12345,  11223, 1122",    // 11222.7…원 → 11223원
                "0,      0,     0"
        })
        void 원_단위_역산_반올림(long total, long expectedSupply, long expectedVat) {
            QuoteAmounts amounts = QuoteAmounts.of(total, VatMode.INCLUDED);

            assertThat(amounts.supplyAmount()).isEqualTo(expectedSupply);
            assertThat(amounts.vatAmount()).isEqualTo(expectedVat);
        }
    }

    /**
     * 이 이슈의 핵심 불변식 — supply와 vat를 각각 반올림하면 여기서 깨진다.
     * INCLUDED에서 vat를 차액으로 구하는 이유다.
     */
    @ParameterizedTest(name = "항목 합계 {0}원 — 두 모드 모두 정합")
    @CsvSource({"1", "5", "7", "99", "999", "1005", "10000", "12345", "999999", "1234567"})
    @DisplayName("어떤 금액에서도 공급가액 + 부가세 = 합계가 성립한다 (QT-25)")
    void 금액_3분리_정합(long itemsTotal) {
        for (VatMode mode : VatMode.values()) {
            QuoteAmounts amounts = QuoteAmounts.of(itemsTotal, mode);

            assertThat(amounts.supplyAmount() + amounts.vatAmount())
                    .as("%s 모드 · 항목 합계 %d원", mode, itemsTotal)
                    .isEqualTo(amounts.totalAmount());
        }
    }

    @Test
    @DisplayName("음수 항목 합계는 거부한다 (단가 0원 하한, Q-02)")
    void 음수_거부() {
        assertThatThrownBy(() -> QuoteAmounts.of(-1L, VatMode.EXCLUDED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
