package com.twojo.quote.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.quote.entity.Quote.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 고객 응답에 따른 견적 상태 전이 — <b>전이표 §6의 표를 그대로 옮긴다</b>.
 *
 * <p>표에 있는 전이가 되는지만 보는 게 아니라 <b>표에 없는 전이가 막히는지</b>를 함께 고정한다.
 * §6이 "표에 없는 전이는 전부 불가"를 원칙으로 두기 때문이다.
 *
 * <pre>
 * 발송됨(SENT)   → 고객 첫 열람 → 열람됨(VIEWED)
 * 열람됨(VIEWED) → 고객 승인   → 승인됨(APPROVED)
 * 열람됨(VIEWED) → 고객 반려   → 반려됨(REJECTED)
 * </pre>
 *
 * <p><b>열람과 응답의 성격이 다르다.</b> 열람은 상태가 안 맞아도 조용히 넘어가고(멱등),
 * 응답은 {@code QUOTE_NOT_RESPONDABLE}로 막힌다. 고객이 링크를 다시 여는 것은 정상이지만
 * 두 번 승인하는 것은 아니기 때문이다 (AP-11).
 */
class QuoteResponseTransitionTest {

    private static final Instant 열람_시각 = Instant.parse("2026-09-07T01:00:00Z");
    private static final Instant 응답_시각 = Instant.parse("2026-09-07T02:00:00Z");

    /**
     * 지정한 상태의 견적. 상태는 리플렉션으로 세운다 — 발송·회수는 아직 없고(다음 이슈),
     * 여기서 보는 것은 응답 전이지 그 앞의 경로가 아니다.
     */
    private static Quote quoteAt(Status status) {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID(),
                "Q-2609-001", LocalDate.now().plusDays(30));
        ReflectionTestUtils.setField(quote, "status", status);
        return quote;
    }

    private static ErrorCode errorOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Nested
    @DisplayName("고객 첫 열람 (AP-02·07)")
    class MarkViewed {

        @Test
        @DisplayName("발송됨이면 열람됨이 되고 첫 열람 시각이 기록된다")
        void 첫_열람() {
            Quote quote = quoteAt(Status.SENT);

            quote.markViewed(열람_시각);

            assertThat(quote.getStatus()).isEqualTo(Status.VIEWED);
            assertThat(quote.getFirstViewedAt()).isEqualTo(열람_시각);
        }

        @Test
        @DisplayName("두 번째 열람은 첫 열람 시각을 덮지 않는다 — '첫' 열람이라는 값의 의미가 사라진다")
        void 재열람은_시각을_밀지_않는다() {
            Quote quote = quoteAt(Status.SENT);
            quote.markViewed(열람_시각);

            quote.markViewed(응답_시각);   // 나중 시각으로 다시 열람

            assertThat(quote.getStatus()).isEqualTo(Status.VIEWED);
            assertThat(quote.getFirstViewedAt()).isEqualTo(열람_시각);   // 그대로
        }

        /**
         * 응답 완료 링크의 <b>열람은 허용</b>이다 (전이표 §7, v1.6.1) — 차단되는 것은 재응답뿐이다.
         * 여기서 예외를 던지면 고객이 자기가 승인한 견적을 다시 못 본다.
         */
        @ParameterizedTest(name = "{0}에서 열람해도 예외가 없다")
        @EnumSource(value = Status.class, names = {"DRAFT", "VIEWED", "APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED"})
        @DisplayName("발송됨이 아니면 조용히 무동작한다 — 예외를 던지지 않는다")
        void 발송됨이_아니면_무동작(Status from) {
            Quote quote = quoteAt(from);

            assertThatCode(() -> quote.markViewed(열람_시각)).doesNotThrowAnyException();

            assertThat(quote.getStatus()).isEqualTo(from);
            assertThat(quote.getFirstViewedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("고객 승인 (AP-08·19)")
    class Approve {

        @Test
        @DisplayName("열람됨이면 승인됨이 되고 응답자가 기록된다")
        void 승인() {
            Quote quote = quoteAt(Status.VIEWED);

            quote.approve("김서연", "구매팀장", 응답_시각);

            assertThat(quote.getStatus()).isEqualTo(Status.APPROVED);
            assertThat(quote.getResponderName()).isEqualTo("김서연");
            assertThat(quote.getResponderTitle()).isEqualTo("구매팀장");
            assertThat(quote.getRespondedAt()).isEqualTo(응답_시각);
        }

        @Test
        @DisplayName("직책은 선택이라 null이어도 승인된다 (AP-19)")
        void 직책은_선택() {
            Quote quote = quoteAt(Status.VIEWED);

            quote.approve("김서연", null, 응답_시각);

            assertThat(quote.getStatus()).isEqualTo(Status.APPROVED);
            assertThat(quote.getResponderTitle()).isNull();
        }

        /**
         * <b>SENT가 여기 들어 있는 것이 이 테스트의 핵심</b>이다. 전이표 §6은 승인을
         * 열람됨에서만 열고, 발송됨에서 바로 승인하는 행은 없다.
         */
        @ParameterizedTest(name = "{0}에서는 승인할 수 없다")
        @EnumSource(value = Status.class, names = {"DRAFT", "SENT", "APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED"})
        @DisplayName("열람됨이 아니면 QUOTE_NOT_RESPONDABLE — 이미 응답한 견적도 막힌다 (AP-11)")
        void 열람됨이_아니면_차단(Status from) {
            Quote quote = quoteAt(from);

            assertThatThrownBy(() -> quote.approve("김서연", "구매팀장", 응답_시각))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteResponseTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_NOT_RESPONDABLE);

            assertThat(quote.getStatus()).isEqualTo(from);          // 실패해도 상태는 그대로
            assertThat(quote.getResponderName()).isNull();
        }
    }

    @Nested
    @DisplayName("고객 반려 (AP-09·10·19)")
    class Reject {

        @Test
        @DisplayName("열람됨이면 반려됨이 되고 사유와 응답자가 기록된다")
        void 반려() {
            Quote quote = quoteAt(Status.VIEWED);

            quote.reject("예산 초과", "김서연", "구매팀장", 응답_시각);

            assertThat(quote.getStatus()).isEqualTo(Status.REJECTED);
            assertThat(quote.getRejectReason()).isEqualTo("예산 초과");
            assertThat(quote.getResponderName()).isEqualTo("김서연");
            assertThat(quote.getRespondedAt()).isEqualTo(응답_시각);
        }

        @ParameterizedTest(name = "{0}에서는 반려할 수 없다")
        @EnumSource(value = Status.class, names = {"DRAFT", "SENT", "APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED"})
        @DisplayName("열람됨이 아니면 QUOTE_NOT_RESPONDABLE")
        void 열람됨이_아니면_차단(Status from) {
            Quote quote = quoteAt(from);

            assertThatThrownBy(() -> quote.reject("예산 초과", "김서연", null, 응답_시각))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteResponseTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_NOT_RESPONDABLE);

            assertThat(quote.getStatus()).isEqualTo(from);
            assertThat(quote.getRejectReason()).isNull();
        }
    }

    /**
     * 응답은 <b>한 번뿐</b>이다 (AP-11). 승인한 견적을 반려로, 반려한 견적을 승인으로
     * 뒤집는 경로가 없다는 것을 고정한다 — 전이표 §6에 그 행이 없다.
     */
    @Test
    @DisplayName("응답한 견적은 반대 응답으로 뒤집히지 않는다 (AP-11)")
    void 재응답_불가() {
        Quote 승인됨 = quoteAt(Status.VIEWED);
        승인됨.approve("김서연", null, 응답_시각);
        assertThatThrownBy(() -> 승인됨.reject("역시 안 되겠습니다", "김서연", null, 응답_시각))
                .extracting(QuoteResponseTransitionTest::errorOf)
                .isEqualTo(ErrorCode.QUOTE_NOT_RESPONDABLE);
        assertThat(승인됨.getStatus()).isEqualTo(Status.APPROVED);

        Quote 반려됨 = quoteAt(Status.VIEWED);
        반려됨.reject("예산 초과", "김서연", null, 응답_시각);
        assertThatThrownBy(() -> 반려됨.approve("김서연", null, 응답_시각))
                .extracting(QuoteResponseTransitionTest::errorOf)
                .isEqualTo(ErrorCode.QUOTE_NOT_RESPONDABLE);
        assertThat(반려됨.getStatus()).isEqualTo(Status.REJECTED);
    }
}
