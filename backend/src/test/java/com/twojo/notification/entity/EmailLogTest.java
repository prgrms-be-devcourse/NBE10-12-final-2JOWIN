package com.twojo.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.MailCommand;
import com.twojo.notification.entity.EmailLog.Status;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailLogTest {

    private static EmailLog scheduled() {
        return EmailLog.schedule(UUID.randomUUID(), MailCommand.TemplateType.QUOTE_SENT, "a@b.com", UUID.randomUUID());
    }

    @Test
    @DisplayName("schedule()은 templateType이 정한 ref_type을 채운다 (파라미터로 받지 않는다)")
    void schedule_derivesRefTypeFromTemplateType() {
        EmailLog log = scheduled();

        assertThat(log.getTemplateType()).isEqualTo(MailCommand.TemplateType.QUOTE_SENT);
        assertThat(log.getRefType()).isEqualTo("QUOTE");
    }

    @Test
    @DisplayName("TemplateType.refType()은 email_log.ref_type에 들어가는 값을 반환한다")
    void templateType_refType() {
        assertThat(MailCommand.TemplateType.QUOTE_SENT.refType()).isEqualTo("QUOTE");
        assertThat(MailCommand.TemplateType.SIGNUP_APPROVED.refType()).isEqualTo("APPLICATION");
        assertThat(MailCommand.TemplateType.PASSWORD_RESET.refType()).isEqualTo("PASSWORD_RESET_TOKEN");
    }

    @Test
    @DisplayName("schedule()로 만든 로그는 SCHEDULED 상태이고 발송 시각은 비어 있다")
    void schedule_scheduled() {
        EmailLog log = scheduled();

        assertThat(log.getStatus()).isEqualTo(Status.SCHEDULED);
        assertThat(log.getSentAt()).isNull();
    }

    @Test
    @DisplayName("markSent()는 SCHEDULED를 SENT로 전이하고 발송 시각을 기록한다")
    void markSent_fromScheduled() {
        EmailLog log = scheduled();
        Instant sentAt = Instant.now();

        log.markSent(sentAt);

        assertThat(log.getStatus()).isEqualTo(Status.SENT);
        assertThat(log.getSentAt()).isEqualTo(sentAt);
    }

    @Test
    @DisplayName("FAILED에서 재시도 성공하면 markSent()가 SENT로 전이한다 (NT-12 재시도 1회)")
    void markSent_fromFailedRetry() {
        EmailLog log = scheduled();
        log.markFailed();
        Instant sentAt = Instant.now();

        log.markSent(sentAt);

        assertThat(log.getStatus()).isEqualTo(Status.SENT);
        assertThat(log.getSentAt()).isEqualTo(sentAt);
    }

    @Test
    @DisplayName("이미 SENT면 markSent()는 최초 발송 시각을 유지하고 아무것도 하지 않는다")
    void markSent_idempotentWhenSent() {
        EmailLog log = scheduled();
        Instant first = Instant.now().minusSeconds(10);
        log.markSent(first);

        log.markSent(Instant.now());

        assertThat(log.getStatus()).isEqualTo(Status.SENT);
        assertThat(log.getSentAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("markFailed()는 SCHEDULED를 FAILED로 전이한다")
    void markFailed_fromScheduled() {
        EmailLog log = scheduled();

        log.markFailed();

        assertThat(log.getStatus()).isEqualTo(Status.FAILED);
    }

    @Test
    @DisplayName("이미 SENT면 markFailed()는 실패로 뒤집지 않는다")
    void markFailed_noOpWhenSent() {
        EmailLog log = scheduled();
        log.markSent(Instant.now());

        log.markFailed();

        assertThat(log.getStatus()).isEqualTo(Status.SENT);
    }
}
