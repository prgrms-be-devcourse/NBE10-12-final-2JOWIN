package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link MailDispatcher} — id로 조회 → status 가드 → 발송 → SENT/FAILED 기록. {@code @Async}·트랜잭션은
 * 목 단위 테스트에서 비활성이라 동기로 로직만 검증한다(발화·격리는 통합 테스트).
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailDispatcherTest {

    private static final UUID EMAIL_LOG_ID = UUID.fromString("9d000000-0000-4000-8000-000000000001");
    private static final UUID REF_ID = UUID.fromString("7a000000-0000-4000-8000-000000000008");

    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private EmailSender emailSender;
    @InjectMocks
    private MailDispatcher mailDispatcher;

    private static EmailLog scheduledRow() {
        EmailLog row = EmailLog.schedule(
                UUID.randomUUID(), MailCommand.TemplateType.QUOTE_SENT, "sujeong@dodam.co.kr", REF_ID);
        ReflectionTestUtils.setField(row, "id", EMAIL_LOG_ID);
        return row;
    }

    private static MailScheduled event() {
        return new MailScheduled(EMAIL_LOG_ID, "[Q-2608-014] 견적서 열람 안내", "본문 http://x/q/raw-token");
    }

    @Test
    @DisplayName("SCHEDULED 행이면 발송하고 SENT로 전이한다")
    void 발송_성공하면_SENT() {
        EmailLog row = scheduledRow();
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));

        mailDispatcher.dispatch(event());

        assertThat(row.getStatus()).isEqualTo(EmailLog.Status.SENT);
        assertThat(row.getSentAt()).isNotNull();
        verify(emailSender).send("sujeong@dodam.co.kr", "[Q-2608-014] 견적서 열람 안내", "본문 http://x/q/raw-token");
    }

    @Test
    @DisplayName("발송이 예외를 던지면 FAILED로 전이한다 (재시도 없음)")
    void 발송_실패하면_FAILED() {
        EmailLog row = scheduledRow();
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));
        willThrow(new RuntimeException("SMTP down")).given(emailSender).send(any(), any(), any());

        mailDispatcher.dispatch(event());

        assertThat(row.getStatus()).isEqualTo(EmailLog.Status.FAILED);
    }

    @Test
    @DisplayName("행 상태가 SCHEDULED가 아니면 발송하지 않는다 (이중 발송 가드)")
    void 이미_SENT면_발송_안_함() {
        EmailLog row = scheduledRow();
        row.markSent(Instant.now());
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));

        mailDispatcher.dispatch(event());

        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("행이 없으면 발송하지 않고 예외도 없다")
    void 행이_없으면_무동작() {
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.empty());

        assertThatCode(() -> mailDispatcher.dispatch(event())).doesNotThrowAnyException();
        verify(emailSender, never()).send(any(), any(), any());
    }
}
