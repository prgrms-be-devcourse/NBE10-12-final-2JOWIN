package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.twojo.boundary.NotificationCommand;
import com.twojo.boundary.NotificationCommand.NotificationType;
import com.twojo.boundary.QuoteCommand;
import com.twojo.boundary.QuoteQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FirstViewRecorder} — 첫 열람 부수효과가 {@code markViewed} → NT-03 순서로 협력자를 부르는지,
 * 각 단계 실패가 그대로 전파되는지 고정한다. 트랜잭션 롤백 자체는 통합 테스트가 커버한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class FirstViewRecorderTest {

    private static final UUID QUOTE_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID DEAL_ID = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String QUOTE_NO = "Q-2609-014";

    @Mock
    private QuoteCommand quoteCommand;
    @Mock
    private NotificationCommand notificationCommand;
    @Mock
    private CustomerNotificationMessages messages;
    @InjectMocks
    private FirstViewRecorder recorder;

    private static QuoteQuery.PublicQuoteView view() {
        return new QuoteQuery.PublicQuoteView(QUOTE_ID, QUOTE_NO, "SENT", "EXCLUSIVE",
                "설치는 납품일로부터 3일 이내", LocalDate.of(2026, 9, 20),
                1_000_000L, 100_000L, 1_100_000L, List.of(), DEAL_ID, COMPANY_ID);
    }

    @Test
    @DisplayName("markViewed 다음에 NT-03(QUOTE_VIEWED)을 딜 컨텍스트로 부른다")
    void markViewed_다음_NT03을_부른다() {
        given(messages.quoteViewed(QUOTE_NO)).willReturn("열람 메시지");

        recorder.recordFirstView(view());

        InOrder order = inOrder(quoteCommand, notificationCommand);
        order.verify(quoteCommand).markViewed(QUOTE_ID);
        order.verify(notificationCommand).notifyForDeal(
                eq(NotificationType.QUOTE_VIEWED), eq(COMPANY_ID), eq(DEAL_ID),
                eq("열람 메시지"), eq(QUOTE_ID));
    }

    @Test
    @DisplayName("markViewed가 던지면 전파하고 NT-03은 부르지 않는다")
    void markViewed_예외는_전파되고_NT03은_생략한다() {
        willThrow(new RuntimeException("boom")).given(quoteCommand).markViewed(QUOTE_ID);

        assertThatThrownBy(() -> recorder.recordFirstView(view()))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(notificationCommand);
    }

    @Test
    @DisplayName("NT-03 발행이 던지면 그대로 전파한다 (호출자가 삼킨다)")
    void NT03_예외는_전파된다() {
        given(messages.quoteViewed(QUOTE_NO)).willReturn("열람 메시지");
        willThrow(new IllegalStateException("no active tx"))
                .given(notificationCommand).notifyForDeal(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> recorder.recordFirstView(view()))
                .isInstanceOf(IllegalStateException.class);
        verify(quoteCommand).markViewed(QUOTE_ID);
    }
}
