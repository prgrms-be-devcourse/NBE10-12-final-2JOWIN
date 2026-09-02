package com.twojo.notification.repository;

import com.twojo.boundary.MailCommand;
import com.twojo.notification.entity.EmailLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {

    /** 이중 발송 사전 체크 — 최종 방어는 DB UNIQUE(template_type, ref_id, recipient_email). */
    boolean existsByTemplateTypeAndRefIdAndRecipientEmail(MailCommand.TemplateType templateType, UUID refId, String recipientEmail);

    /** 재시도·운영 지표용 상태별 조회 (예: FAILED). */
    List<EmailLog> findByStatus(EmailLog.Status status);
}
