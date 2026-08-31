package com.twojo.quote.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 견적 금액 3분리 — 공급가액·부가세·합계 (QT-08·22·25, Q-03).
 *
 * <p><b>항상 서버 계산이다</b> — 요청 DTO에 supply·vat·total 필드 자체가 없다 (08-dto.md 검증 노트 #1).
 * 저장은 원 단위 정수(Q-12, ERD BIGINT)이고, 나눗셈 구간만 {@link BigDecimal}로 계산해 원 단위로 반올림한다.
 *
 * <p><b>단수 처리는 원 단위 반올림(HALF_UP)이다</b> — 요구사항 QT-22·23은 "자동 계산"과
 * "별도/포함 지정"까지만 정하지만, 11-work-breakdown.md §4의 C 검증 테스트가
 * "1원·999원·홀수 금액 반올림"으로 적고 있어 그에 맞춘다.
 * <pre>
 * EXCLUDED(기본, Q-16)  supply = 항목 합계             -- 정수 연산, 오차 없음
 *                       vat    = round(supply x 0.1)
 *                       total  = supply + vat
 *
 * INCLUDED              total  = 항목 합계             -- 입력 단가가 세포함
 *                       supply = round(total / 1.1)
 *                       vat    = total - supply        -- 차액으로 구한다
 * </pre>
 * <p>INCLUDED에서 vat를 따로 반올림하지 않고 <b>차액으로 구한다</b>.
 * 10% · HALF_UP 조합에서는 vat를 직접 반올림해도 결과가 같지만(전수 검증 확인),
 * 차액 방식은 그 성질에 기대지 않고 {@code supply + vat == total}을
 * <b>구조적으로 보장</b>한다 — QT-25의 3분리 표시가 어떤 금액에서도 깨지지 않는다.
 *
 * <p>단수 처리 방식을 바꾸려면 {@link #VAT_ROUNDING} 한 곳만 고치면 된다.
 * 다만 <b>발송된 견적이 쌓인 뒤에 바꾸면 과거 금액이 달라진다</b> (PB-04).
 */
public record QuoteAmounts(long supplyAmount, long vatAmount, long totalAmount) {

    /** 부가가치세율 10% — 원화 국내 거래 단일 세율 (Q-12) */
    private static final BigDecimal VAT_RATE = new BigDecimal("0.1");

    /** 세포함 금액에서 공급가액을 역산하는 제수 (1 + VAT_RATE) */
    private static final BigDecimal VAT_INCLUDED_DIVISOR = new BigDecimal("1.1");

    /** 원 미만 단수 처리 — <b>반올림</b> (11-work-breakdown.md §4). 변경 지점은 여기 하나뿐이다 */
    private static final RoundingMode VAT_ROUNDING = RoundingMode.HALF_UP;

    public QuoteAmounts {
        if (supplyAmount < 0 || vatAmount < 0 || totalAmount < 0) {
            throw new IllegalArgumentException("견적 금액은 음수일 수 없습니다.");
        }
        if (supplyAmount + vatAmount != totalAmount) {
            throw new IllegalArgumentException(
                    "공급가액 + 부가세가 합계와 일치하지 않습니다: %d + %d != %d"
                            .formatted(supplyAmount, vatAmount, totalAmount));
        }
    }

    /**
     * 항목 합계로부터 3분리 금액을 계산한다.
     *
     * @param itemsTotal 항목 amount의 합 — EXCLUDED면 공급가액, INCLUDED면 세포함 합계
     */
    public static QuoteAmounts of(long itemsTotal, Quote.VatMode vatMode) {
        if (itemsTotal < 0) {
            throw new IllegalArgumentException("항목 합계는 음수일 수 없습니다: " + itemsTotal);
        }
        return vatMode == Quote.VatMode.INCLUDED ? included(itemsTotal) : excluded(itemsTotal);
    }

    /** 부가세 별도 — 항목 합계가 공급가액이다 (Q-16 기본) */
    private static QuoteAmounts excluded(long supply) {
        long vat = BigDecimal.valueOf(supply)
                .multiply(VAT_RATE)
                .setScale(0, VAT_ROUNDING)
                .longValueExact();
        return new QuoteAmounts(supply, vat, supply + vat);
    }

    /** 부가세 포함 — 항목 합계가 세포함 합계다. vat는 차액으로 구해 정합을 보장한다 */
    private static QuoteAmounts included(long total) {
        long supply = BigDecimal.valueOf(total)
                .divide(VAT_INCLUDED_DIVISOR, 0, VAT_ROUNDING)
                .longValueExact();
        return new QuoteAmounts(supply, total - supply, total);
    }
}
