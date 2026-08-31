package com.twojo.quote.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.quote.entity.Quote.VatMode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 견적 엔티티 — 항목 교체 시 금액 재계산 (QT-02~08·22·23) · 발송 후 불변 (QT-14·16). */
class QuoteTest {

    private static QuoteItem item(String name, int quantity, long unitPrice, int sortOrder) {
        return QuoteItem.of(UUID.randomUUID(), name, "개", quantity, unitPrice, unitPrice, sortOrder);
    }

    @Test
    @DisplayName("작성 시작하면 DRAFT · 부가세 별도 · 금액 0원이다 (QT-01, Q-16)")
    void 작성_시작() {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID());

        assertThat(quote.getStatus()).isEqualTo(Quote.Status.DRAFT);
        assertThat(quote.getVatMode()).isEqualTo(VatMode.EXCLUDED);
        assertThat(quote.getTotalAmount()).isZero();
    }

    @Test
    @DisplayName("항목 amount는 단가 x 수량으로 서버가 계산한다 (QT-08)")
    void 항목_금액_계산() {
        QuoteItem item = item("메쉬 의자", 3, 100_000L, 0);

        assertThat(item.getAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("항목을 교체하면 공급가액이 항목 합계로 재계산된다 (QT-02~08)")
    void 항목_교체시_재계산() {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID());

        quote.replaceItems(List.of(
                item("1600 사무책상", 2, 240_000L, 0),   // 480,000
                item("메쉬 의자", 4, 100_000L, 1)));      // 400,000

        assertThat(quote.getSupplyAmount()).isEqualTo(880_000L);
        assertThat(quote.getVatAmount()).isEqualTo(88_000L);
        assertThat(quote.getTotalAmount()).isEqualTo(968_000L);
        assertThat(quote.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("항목 교체는 누적이 아니라 전체 대체다 — 기존 항목은 사라진다")
    void 항목_교체는_전체_대체() {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID());
        quote.replaceItems(List.of(item("파티션", 10, 90_000L, 0)));

        quote.replaceItems(List.of(item("회의 테이블", 1, 350_000L, 0)));

        assertThat(quote.getItems()).hasSize(1);
        assertThat(quote.getSupplyAmount()).isEqualTo(350_000L);
    }

    @Test
    @DisplayName("부가세 포함으로 바꾸면 같은 항목 합계를 세포함으로 다시 해석한다 (QT-23)")
    void 부가세_모드_변경시_재계산() {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID());
        quote.replaceItems(List.of(item("패브릭 소파", 2, 450_000L, 0)));   // 900,000

        quote.changeVatMode(VatMode.INCLUDED);

        assertThat(quote.getTotalAmount()).isEqualTo(900_000L);
        assertThat(quote.getSupplyAmount()).isEqualTo(818_182L);   // 900,000 / 1.1 반올림
        assertThat(quote.getVatAmount()).isEqualTo(81_818L);
        assertThat(quote.getSupplyAmount() + quote.getVatAmount()).isEqualTo(quote.getTotalAmount());
    }

    @Test
    @DisplayName("수량 0 이하는 거부한다")
    void 수량_하한() {
        assertThatThrownBy(() -> item("메쉬 의자", 0, 100_000L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 단가는 거부한다 — 0원 하한 (Q-02)")
    void 단가_하한() {
        assertThatThrownBy(() -> item("메쉬 의자", 1, -1L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("단가 0원 항목은 허용한다 (Q-02 — 할인 대신 단가 조정)")
    void 단가_0원_허용() {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID());

        quote.replaceItems(List.of(item("설치 서비스", 1, 0L, 0)));

        assertThat(quote.getTotalAmount()).isZero();
    }

    @Test
    @DisplayName("발송된 견적은 항목을 바꿀 수 없다 — QUOTE_NOT_DRAFT (QT-14·16)")
    void 발송_후_불변() {
        Quote quote = sentQuote();

        assertThatThrownBy(() -> quote.replaceItems(List.of(item("메쉬 의자", 1, 100_000L, 0))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.QUOTE_NOT_DRAFT);
    }

    @Test
    @DisplayName("발송된 견적은 부가세 모드도 바꿀 수 없다")
    void 발송_후_부가세_모드_불변() {
        Quote quote = sentQuote();

        assertThatThrownBy(() -> quote.changeVatMode(VatMode.INCLUDED))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 발송 전이는 아직 미구현이라 리플렉션으로 상태만 만든다.
     * TODO: 발송 구현 이슈에서 {@code quote.send(...)} 로 교체한다.
     */
    private static Quote sentQuote() {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID());
        quote.replaceItems(List.of(item("메쉬 의자", 1, 100_000L, 0)));
        try {
            var field = Quote.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(quote, Quote.Status.SENT);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return quote;
    }
}
