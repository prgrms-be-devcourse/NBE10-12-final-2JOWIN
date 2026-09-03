package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.twojo.boundary.MailCommand;
import com.twojo.notification.entity.EmailLog;
import com.twojo.notification.repository.EmailLogRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link MailCommandImpl} — SCHEDULED 행을 저장하고 저장된 id로 {@link MailScheduled}를 발행한다.
 * {@code @Transactional(MANDATORY)}의 "트랜잭션 밖 호출 거부"는 프록시가 필요해 통합 테스트가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailCommandImplTest {

    private static final UUID EMAIL_LOG_ID = UUID.fromString("9d000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID REF_ID = UUID.fromString("7a000000-0000-4000-8000-000000000008");

    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private MailCommandImpl mailCommand;

    private void givenSaveStampsId() {
        given(emailLogRepository.save(any(EmailLog.class))).willAnswer(inv -> {
            EmailLog row = inv.getArgument(0);
            ReflectionTestUtils.setField(row, "id", EMAIL_LOG_ID);
            return row;
        });
    }

    @Test
    @DisplayName("SCHEDULED 행을 저장하고, 저장된 id로 subject·body를 담은 MailScheduled를 발행한다")
    void SCHEDULED_저장하고_이벤트_발행() {
        givenSaveStampsId();

        mailCommand.schedule(MailCommand.TemplateType.QUOTE_SENT, COMPANY_ID, "sujeong@dodam.co.kr",
                REF_ID, "제목", "본문 http://x/q/raw");

        ArgumentCaptor<EmailLog> saved = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(EmailLog.Status.SCHEDULED);
        assertThat(saved.getValue().getTemplateType()).isEqualTo(MailCommand.TemplateType.QUOTE_SENT);
        assertThat(saved.getValue().getRefId()).isEqualTo(REF_ID);
        assertThat(saved.getValue().getRecipientEmail()).isEqualTo("sujeong@dodam.co.kr");

        ArgumentCaptor<MailScheduled> event = ArgumentCaptor.forClass(MailScheduled.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().emailLogId()).isEqualTo(EMAIL_LOG_ID);
        assertThat(event.getValue().subject()).isEqualTo("제목");
        assertThat(event.getValue().body()).isEqualTo("본문 http://x/q/raw");
    }

    @Test
    @DisplayName("refId가 null이면 예외를 전파한다 (uk_email_log_dedup 무력화 방지)")
    void refId_null이면_예외() {
        assertThatThrownBy(() -> mailCommand.schedule(
                MailCommand.TemplateType.QUOTE_SENT, COMPANY_ID, "a@b.com", null, "제목", "본문"))
                .isInstanceOf(NullPointerException.class);
    }
}
