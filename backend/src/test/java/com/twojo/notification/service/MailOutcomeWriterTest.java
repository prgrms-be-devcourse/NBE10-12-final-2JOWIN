package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.MailCommand;
import com.twojo.notification.entity.EmailLog;
import com.twojo.notification.repository.EmailLogRepository;
import java.time.Instant;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailOutcomeWriterTest {

    private static final UUID EMAIL_LOG_ID = UUID.fromString("9d000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("1c000000-0000-4000-8000-000000000001");
    private static final UUID REF_ID = UUID.fromString("7a000000-0000-4000-8000-000000000008");

    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private MailOutcomeWriter writer;

    private static EmailLog scheduledRow() {
        return EmailLog.schedule(COMPANY_ID, MailCommand.TemplateType.QUOTE_SENT, "a@b.com", REF_ID);
    }

    @Test
    @DisplayName("markFailed — SCHEDULED 행을 FAILED로 전이한다")
    void markFailed_SCHEDULED를_FAILED로() {
        EmailLog row = scheduledRow();
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));

        writer.markFailed(EMAIL_LOG_ID);

        assertThat(row.getStatus()).isEqualTo(EmailLog.Status.FAILED);
    }

    @Test
    @DisplayName("markFailed — 실제 전이 시 EmailDeliveryFailedEvent를 email_log 정보로 발행한다")
    void markFailed_전이하면_이벤트를_발행한다() {
        EmailLog row = scheduledRow();
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));

        writer.markFailed(EMAIL_LOG_ID);

        ArgumentCaptor<EmailDeliveryFailedEvent> event = ArgumentCaptor.forClass(EmailDeliveryFailedEvent.class);
        then(eventPublisher).should().publishEvent(event.capture());
        assertThat(event.getValue().templateType()).isEqualTo(MailCommand.TemplateType.QUOTE_SENT);
        assertThat(event.getValue().companyId()).isEqualTo(COMPANY_ID);
        assertThat(event.getValue().refId()).isEqualTo(REF_ID);
    }

    @Test
    @DisplayName("markFailed — 이미 FAILED면 이벤트를 발행하지 않는다 (중복 알림 방지)")
    void markFailed_이미_FAILED면_이벤트없음() {
        EmailLog row = scheduledRow();
        row.markFailed();
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));

        writer.markFailed(EMAIL_LOG_ID);

        then(eventPublisher).should(never()).publishEvent(any(EmailDeliveryFailedEvent.class));
    }

    @Test
    @DisplayName("markFailed — 이미 SENT면 실패로 뒤집지 않고 이벤트도 없다")
    void markFailed_이미_SENT면_이벤트없음() {
        EmailLog row = scheduledRow();
        row.markSent(Instant.parse("2026-09-08T01:00:00Z"));
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));

        writer.markFailed(EMAIL_LOG_ID);

        assertThat(row.getStatus()).isEqualTo(EmailLog.Status.SENT);
        then(eventPublisher).should(never()).publishEvent(any(EmailDeliveryFailedEvent.class));
    }

    @Test
    @DisplayName("markFailed — 행이 없으면 예외 없이 무동작하고 이벤트도 없다")
    void markFailed_행이_없으면_무동작() {
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.empty());

        assertThatCode(() -> writer.markFailed(EMAIL_LOG_ID)).doesNotThrowAnyException();
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("markSent — SCHEDULED 행을 SENT로 전이하고 발송 시각을 기록한다")
    void markSent_SCHEDULED를_SENT로() {
        EmailLog row = scheduledRow();
        Instant sentAt = Instant.parse("2026-09-08T01:00:00Z");
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));

        writer.markSent(EMAIL_LOG_ID, sentAt);

        assertThat(row.getStatus()).isEqualTo(EmailLog.Status.SENT);
        assertThat(row.getSentAt()).isEqualTo(sentAt);
    }

    @Test
    @DisplayName("markSent — 이미 SENT면 최초 발송 시각을 유지한다 (멱등)")
    void markSent_이미_SENT면_무동작() {
        EmailLog row = scheduledRow();
        Instant first = Instant.parse("2026-09-08T01:00:00Z");
        row.markSent(first);
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));

        writer.markSent(EMAIL_LOG_ID, Instant.parse("2026-09-08T02:00:00Z"));

        assertThat(row.getSentAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("markSent — 행이 없으면 예외 없이 무동작한다")
    void markSent_행이_없으면_무동작() {
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.empty());

        assertThatCode(() -> writer.markSent(EMAIL_LOG_ID, Instant.now())).doesNotThrowAnyException();
    }
}
