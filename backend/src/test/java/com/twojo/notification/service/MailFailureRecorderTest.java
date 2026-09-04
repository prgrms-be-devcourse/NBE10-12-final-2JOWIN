package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.MailCommand;
import com.twojo.notification.entity.EmailLog;
import com.twojo.notification.repository.EmailLogRepository;
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

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailFailureRecorderTest {

    private static final UUID EMAIL_LOG_ID = UUID.fromString("9d000000-0000-4000-8000-000000000001");

    @Mock
    private EmailLogRepository emailLogRepository;
    @InjectMocks
    private MailFailureRecorder recorder;

    @Test
    @DisplayName("SCHEDULED 행을 FAILED로 전이한다")
    void SCHEDULED를_FAILED로() {
        EmailLog row = EmailLog.schedule(
                UUID.randomUUID(), MailCommand.TemplateType.QUOTE_SENT, "a@b.com",
                UUID.fromString("7a000000-0000-4000-8000-000000000008"));
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.of(row));

        recorder.markFailed(EMAIL_LOG_ID);

        assertThat(row.getStatus()).isEqualTo(EmailLog.Status.FAILED);
    }

    @Test
    @DisplayName("행이 없으면 예외 없이 무동작한다")
    void 행이_없으면_무동작() {
        given(emailLogRepository.findById(EMAIL_LOG_ID)).willReturn(Optional.empty());

        assertThatCode(() -> recorder.markFailed(EMAIL_LOG_ID)).doesNotThrowAnyException();
    }
}
