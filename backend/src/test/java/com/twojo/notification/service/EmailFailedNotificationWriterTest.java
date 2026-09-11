package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.twojo.boundary.MailCommand;
import com.twojo.boundary.NotificationCommand;
import com.twojo.boundary.NotificationCommand.NotificationType;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.ViewTokenQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmailFailedNotificationWriterTest {

    private static final UUID EMAIL_LOG_ID = UUID.fromString("9d000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("1c000000-0000-4000-8000-000000000001");
    private static final UUID TOKEN_ID = UUID.fromString("7a000000-0000-4000-8000-000000000008");
    private static final UUID QUOTE_ID = UUID.fromString("6b000000-0000-4000-8000-000000000001");
    private static final UUID DEAL_ID = UUID.fromString("d0000000-0000-4000-8000-000000000001");

    @Mock private ViewTokenQuery viewTokenQuery;
    @Mock private QuoteQuery quoteQuery;
    @Mock private NotificationCommand notificationCommand;
    @InjectMocks private EmailFailedNotificationWriter writer;

    private static EmailDeliveryFailedEvent event() {
        return new EmailDeliveryFailedEvent(
                EMAIL_LOG_ID, MailCommand.TemplateType.QUOTE_SENT, COMPANY_ID, TOKEN_ID);
    }

    private static QuoteQuery.PublicQuoteView view() {
        return new QuoteQuery.PublicQuoteView(QUOTE_ID, "Q-2026-011", "SENT", "EXCLUDED", "약관",
                LocalDate.of(2026, 9, 30), 1000L, 100L, 1100L, List.of(), DEAL_ID, COMPANY_ID);
    }

    @Test
    @DisplayName("토큰을 견적·딜로 되짚어 notifyForDeal(EMAIL_FAILED)로 담당자에게 알린다")
    void 토큰을_되짚어_담당자에게_알린다() {
        given(viewTokenQuery.quoteIdOf(TOKEN_ID)).willReturn(Optional.of(QUOTE_ID));
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());

        writer.write(event());

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        then(notificationCommand).should().notifyForDeal(
                eq(NotificationType.EMAIL_FAILED), eq(COMPANY_ID), eq(DEAL_ID), msg.capture(), eq(QUOTE_ID));
        assertThat(msg.getValue()).contains("Q-2026-011").contains("재발송");
    }

    @Test
    @DisplayName("토큰 행이 없으면 알림 없이 조용히 끝낸다")
    void 토큰_행이_없으면_무동작한다() {
        given(viewTokenQuery.quoteIdOf(TOKEN_ID)).willReturn(Optional.empty());

        writer.write(event());

        then(quoteQuery).shouldHaveNoInteractions();
        then(notificationCommand).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("getPublicView가 던지면 그대로 전파한다 (리스너가 삼킨다)")
    void getPublicView_예외는_전파한다() {
        given(viewTokenQuery.quoteIdOf(TOKEN_ID)).willReturn(Optional.of(QUOTE_ID));
        given(quoteQuery.getPublicView(QUOTE_ID)).willThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> writer.write(event())).isInstanceOf(RuntimeException.class);
        then(notificationCommand).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("QUOTE_REMIND는 refId를 견적 id로 직접 써서 담당자에게 알린다 (토큰 되짚기 없음)")
    void QUOTE_REMIND는_refId를_견적으로_직접_쓴다() {
        EmailDeliveryFailedEvent e = new EmailDeliveryFailedEvent(
                EMAIL_LOG_ID, MailCommand.TemplateType.QUOTE_REMIND, COMPANY_ID, QUOTE_ID);
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());

        writer.write(e);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        then(notificationCommand).should().notifyForDeal(
                eq(NotificationType.EMAIL_FAILED), eq(COMPANY_ID), eq(DEAL_ID), msg.capture(), eq(QUOTE_ID));
        assertThat(msg.getValue()).contains("Q-2026-011").contains("리마인드");
        then(viewTokenQuery).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("QUOTE_EXPIRING은 refId를 견적 id로 직접 써서 담당자에게 알린다 (토큰 되짚기 없음)")
    void QUOTE_EXPIRING은_refId를_견적으로_직접_쓴다() {
        EmailDeliveryFailedEvent e = new EmailDeliveryFailedEvent(
                EMAIL_LOG_ID, MailCommand.TemplateType.QUOTE_EXPIRING, COMPANY_ID, QUOTE_ID);
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());

        writer.write(e);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        then(notificationCommand).should().notifyForDeal(
                eq(NotificationType.EMAIL_FAILED), eq(COMPANY_ID), eq(DEAL_ID), msg.capture(), eq(QUOTE_ID));
        assertThat(msg.getValue()).contains("Q-2026-011").contains("유효기간 임박");
        then(viewTokenQuery).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("처리 못하는 template이면 정합 깨짐 - IllegalStateException (notifiesInApp ↔ write 방어선)")
    void 처리_못하는_template이면_IllegalStateException() {
        EmailDeliveryFailedEvent wrong = new EmailDeliveryFailedEvent(
                EMAIL_LOG_ID, MailCommand.TemplateType.PASSWORD_RESET, COMPANY_ID, TOKEN_ID);

        assertThatThrownBy(() -> writer.write(wrong)).isInstanceOf(IllegalStateException.class);
        then(viewTokenQuery).shouldHaveNoInteractions();
        then(notificationCommand).shouldHaveNoInteractions();
    }
}
