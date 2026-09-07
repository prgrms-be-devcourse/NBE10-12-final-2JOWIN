package com.twojo.quote.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 견적 금액 3분리 — 공급가액·부가세·합계 (QT-08·22·25, Q-03).
 *
 * <p><b>항상 서버 계산이다</b> — 요청 DTO에 supply·vat·total 필드 자체가 없다 (08-dto.md 검증 노트 #1).
 * 저장은 원 단위 정수(Q-12, ERD BIGINT)이고, 곱셈 결과만 원 단위로 반올림한다(Q-45).
 *
 * <pre>
 * supply = 항목 합계             -- 단가는 항상 세전 (Q-46)
 * vat    = round(supply x 0.1)   -- 공급가액으로부터 계산 (QT-22)
 * total  = supply + vat
 * </pre>
 *
 * <p><b>{@code vat_mode}를 받지 않는다</b> (Q-46). 부가세 별도·포함은 <b>견적서 표시 기준</b>이고
 * 금액에는 영향이 없다 — 두 모드의 supply·vat·total이 같다. 예전에는 INCLUDED를
 * 역산({@code 합계 / 1.1})으로 구현했는데, 그러면 QT-22("부가세는 <b>공급가액으로부터</b>
 * 자동으로 계산된다")와 어긋난다. 총액을 입력값 그대로 고정하면 부가세가 차액이 되어 QT-22를
 * 벗어나고, QT-22를 지키면 총액이 입력값과 달라진다 — <b>둘 다는 불가능하다.</b>
 * 단가를 항상 세전으로 두면 이 딜레마가 없고, 카탈로그 값 복사(QT-24)에 환산도 필요 없다.
 * "부가세 포함 총액"으로 협상한 경우 담당자가 단가를 직접 조정한다(QT-05).
 *
 * <p>단수 처리 방식을 바꾸려면 {@link #VAT_ROUNDING} 한 곳만 고치면 된다.
 * 다만 <b>발송된 견적이 쌓인 뒤에 바꾸면 과거 금액이 달라진다</b> (PB-04).
 */
public record QuoteAmounts(long supplyAmount, long vatAmount, long totalAmount) {

    /** 부가가치세율 10% — 원화 국내 거래 단일 세율 (Q-12) */
    private static final BigDecimal VAT_RATE = new BigDecimal("0.1");

    /** 원 미만 단수 처리 — <b>반올림</b> (Q-45). 변경 지점은 여기 하나뿐이다 */
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
     * @param itemsTotal 항목 amount의 합 — 단가가 세전이므로 이 값이 곧 공급가액이다
     */
    public static QuoteAmounts of(long itemsTotal) {
        if (itemsTotal < 0) {
            throw new IllegalArgumentException("항목 합계는 음수일 수 없습니다: " + itemsTotal);
        }
        long vat = BigDecimal.valueOf(itemsTotal)
                .multiply(VAT_RATE)
                .setScale(0, VAT_ROUNDING)
                .longValueExact();
        return new QuoteAmounts(itemsTotal, vat, itemsTotal + vat);
    }
}
