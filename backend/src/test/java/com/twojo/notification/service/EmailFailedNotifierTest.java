package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.twojo.boundary.MailCommand;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmailFailedNotifierTest {

    private static final UUID EMAIL_LOG_ID = UUID.fromString("9d000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("1c000000-0000-4000-8000-000000000001");
    private static final UUID REF_ID = UUID.fromString("7a000000-0000-4000-8000-000000000008");

    @Mock
    private EmailFailedNotificationWriter writer;
    @InjectMocks
    private EmailFailedNotifier notifier;

    private static EmailDeliveryFailedEvent event(MailCommand.TemplateType type) {
        return new EmailDeliveryFailedEvent(EMAIL_LOG_ID, type, COMPANY_ID, REF_ID);
    }

    @Test
    @DisplayName("QUOTE_SENT 실패면 writer에 위임한다")
    void QUOTE_SENT면_writer에_위임한다() {
        EmailDeliveryFailedEvent e = event(MailCommand.TemplateType.QUOTE_SENT);

        notifier.on(e);

        then(writer).should().write(e);
    }

    @Test
    @DisplayName("QUOTE_SENT가 아닌 실패는 무시한다 (v1 스코프)")
    void 다른_template은_무시한다() {
        notifier.on(event(MailCommand.TemplateType.PASSWORD_RESET));

        then(writer).should(never()).write(any());
    }

    @Test
    @DisplayName("writer가 던져도 리스너는 예외를 전파하지 않는다 (email_log FAILED가 감지 바닥)")
    void writer_예외는_삼킨다() {
        EmailDeliveryFailedEvent e = event(MailCommand.TemplateType.QUOTE_SENT);
        willThrow(new RuntimeException("boom")).given(writer).write(e);

        assertThatCode(() -> notifier.on(e)).doesNotThrowAnyException();
    }
}
