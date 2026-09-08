package com.twojo.quote.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.quote.entity.Quote.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 발송·회수·재발송의 상태 규칙 — <b>전이표 §6의 표를 그대로 옮긴다</b>.
 *
 * <pre>
 * 작성 중(DRAFT)   → 발송 → 발송됨(SENT)      · 항목 1개 이상 (QT-15)
 * 발송됨 · 열람됨   → 회수 → 회수됨(WITHDRAWN) · 종결
 * </pre>
 *
 * <p>표에 있는 전이가 되는지만이 아니라 <b>표에 없는 전이가 막히는지</b>를 함께 고정한다.
 *
 * <p><b>견적 밖의 조건은 여기서 보지 않는다</b> — 종결 Deal 차단(Q-25)·수신인 검증은
 * 서비스가 경계 계약으로 판정한다. 엔티티가 그걸 알면 단위 테스트에 DB가 딸려온다.
 */
class QuoteSendTransitionTest {

    private static final LocalDate 오늘 = LocalDate.of(2026, 9, 7);
    private static final Instant 발송_시각 = Instant.parse("2026-09-07T01:00:00Z");

    /** 항목 1건이 든 견적. 상태는 리플렉션으로 세운다 — 여기서 보는 것은 전이지 그 앞의 경로가 아니다 */
    private static Quote quoteAt(Status status, LocalDate validUntil) {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID(), "Q-2609-001", validUntil);
        quote.replaceItems(List.of(QuoteItem.of(null, "현장 실측", "식", 1, 300_000L, null, 0)));
        ReflectionTestUtils.setField(quote, "status", status);
        return quote;
    }

    private static Quote quoteAt(Status status) {
        return quoteAt(status, 오늘.plusDays(15));
    }

    private static ErrorCode errorOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Nested
    @DisplayName("발송 가능 검사 (QT-14~16, Q-17)")
    class RequireSendable {

        @Test
        @DisplayName("작성 중이고 항목이 있고 유효기간이 남아 있으면 통과한다")
        void 통과() {
            assertThatCode(() -> quoteAt(Status.DRAFT).requireSendable(오늘)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("유효기간 당일도 발송된다 — 그날 23:59:59까지 유효하다 (Q-17)")
        void 당일은_허용() {
            Quote quote = quoteAt(Status.DRAFT, 오늘);

            assertThatCode(() -> quote.requireSendable(오늘)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("유효기간이 지났으면 막힌다 — 입력은 @Future가 막지만 저장된 값이 낡는 건 못 막는다")
        void 유효기간_경과() {
            Quote quote = quoteAt(Status.DRAFT, 오늘.minusDays(1));

            assertThatThrownBy(() -> quote.requireSendable(오늘))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteSendTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_VALID_UNTIL_PASSED);
        }

        @Test
        @DisplayName("항목이 없으면 막힌다 (QT-15)")
        void 빈_항목() {
            Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID(), "Q-2609-001", 오늘.plusDays(15));

            assertThatThrownBy(() -> quote.requireSendable(오늘))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteSendTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_EMPTY_ITEMS);
        }

        @ParameterizedTest(name = "{0}은 발송할 수 없다")
        @EnumSource(value = Status.class, names = {"SENT", "VIEWED", "APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED"})
        @DisplayName("작성 중이 아니면 막힌다 (QT-14·16)")
        void 작성_중이_아니면_차단(Status from) {
            assertThatThrownBy(() -> quoteAt(from).requireSendable(오늘))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteSendTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_NOT_DRAFT);
        }
    }

    @Nested
    @DisplayName("발송 확정 (QT-13)")
    class MarkSent {

        @Test
        @DisplayName("발송됨이 되고 발송 시각이 남는다")
        void 발송() {
            Quote quote = quoteAt(Status.DRAFT);

            quote.markSent(발송_시각);

            assertThat(quote.getStatus()).isEqualTo(Status.SENT);
            assertThat(quote.getSentAt()).isEqualTo(발송_시각);
        }

        /**
         * 검증({@code requireSendable})과 전이가 나뉜 것은 링크 발급 순서 때문이지
         * 전이가 무방비여도 된다는 뜻이 아니다 — 순서를 건너뛴 호출은 여기서 막힌다.
         */
        @ParameterizedTest(name = "{0}에서 markSent는 막힌다")
        @EnumSource(value = Status.class, names = {"SENT", "VIEWED", "APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED"})
        @DisplayName("작성 중이 아니면 전이 자체가 막힌다 — 검증을 건너뛴 호출 방지")
        void 순서를_건너뛴_호출_차단(Status from) {
            assertThatThrownBy(() -> quoteAt(from).markSent(발송_시각))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteSendTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_NOT_DRAFT);
        }
    }

    @Nested
    @DisplayName("회수 (QT-17)")
    class Withdraw {

        @ParameterizedTest(name = "{0} → WITHDRAWN")
        @EnumSource(value = Status.class, names = {"SENT", "VIEWED"})
        @DisplayName("발송됨·열람됨은 회수된다")
        void 회수(Status from) {
            Quote quote = quoteAt(from);

            quote.withdraw();

            assertThat(quote.getStatus()).isEqualTo(Status.WITHDRAWN);
        }

        @ParameterizedTest(name = "{0}은 회수할 수 없다")
        @EnumSource(value = Status.class, names = {"DRAFT", "APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED"})
        @DisplayName("그 밖의 상태는 QUOTE_NOT_WITHDRAWABLE — 응답이 끝난 견적은 되돌리지 않는다")
        void 그_밖은_차단(Status from) {
            assertThatThrownBy(() -> quoteAt(from).withdraw())
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteSendTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_NOT_WITHDRAWABLE);
        }
    }

    @Nested
    @DisplayName("재발송 가능 검사 (AP-13)")
    class RequireResendable {

        @ParameterizedTest(name = "{0}은 재발송할 수 있다")
        @EnumSource(value = Status.class, names = {"SENT", "VIEWED"})
        @DisplayName("발송됨·열람됨이면 통과한다")
        void 통과(Status from) {
            assertThatCode(() -> quoteAt(from).requireResendable()).doesNotThrowAnyException();
        }

        /**
         * <b>판정 축이 링크가 아니라 견적 상태</b>라는 것을 고정한다 — 수동 만료(AP-14)로
         * 링크를 닫은 뒤에도 견적이 SENT·VIEWED면 다른 수신인에게 다시 보낼 수 있어야 한다.
         */
        @ParameterizedTest(name = "{0}은 재발송할 수 없다")
        @EnumSource(value = Status.class, names = {"DRAFT", "APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED"})
        @DisplayName("그 밖의 상태는 QUOTE_NOT_RESENDABLE")
        void 그_밖은_차단(Status from) {
            assertThatThrownBy(() -> quoteAt(from).requireResendable())
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteSendTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_NOT_RESENDABLE);
        }
    }
}
