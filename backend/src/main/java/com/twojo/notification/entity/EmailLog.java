package com.twojo.notification.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시스템 메일 발송 기록 (NT-01~06·10·13) — UNIQUE(template_type, ref_id, recipient_email)가
 * 배치 재실행 이중 발송을 DB에서 차단. 수신자가 바뀌면 키가 달라져 새 담당자에게 정상 발송.
 * FAILED는 운영 지표 — NT-13 실패의 유일한 감지 경로 (14-tech-stack.md §1.5).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailLog extends BaseTimeEntity {

    public enum Status { SCHEDULED, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;   // 플랫폼 발송(NT-13)은 null

    private String templateType;   // NT-01~06·10·13

    private String recipientEmail;

    private String refType;

    private UUID refId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant sentAt;

    /** 발송 예약 — SCHEDULED 상태로 생성. companyId는 플랫폼 전역 메일(NT-13)이면 null. */
    public static EmailLog schedule(UUID companyId, String templateType, String recipientEmail,
                                    String refType, UUID refId) {
        EmailLog log = new EmailLog();
        log.companyId = companyId;   // NT-13 플랫폼 발송은 null 허용
        log.templateType = Objects.requireNonNull(templateType, "templateType");
        log.recipientEmail = Objects.requireNonNull(recipientEmail, "recipientEmail");
        log.refType = refType;
        log.refId = refId;
        log.status = Status.SCHEDULED;
        return log;
    }

    /**
     * 발송 성공 — SENT + 발송 시각 기록. 이미 SENT면 무동작(멱등).
     * sentAt은 커밋 후 비동기(AFTER_COMMIT) 발송이 실제 완료된 시각이라 파라미터로 받는다.
     * 재시도 성공(FAILED → SENT)도 허용한다 (NT-12 재시도 1회).
     */
    public void markSent(Instant sentAt) {
        if (status == Status.SENT) {
            return;
        }
        this.status = Status.SENT;
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
    }

    /**
     * 발송 실패 — FAILED. 이미 SENT면 무동작(발송 성공은 뒤집지 않는다).
     * 재시도 1회 후에도 실패면 인앱 EMAIL_FAILED로 통지 (NT-12).
     */
    public void markFailed() {
        if (status == Status.SENT) {
            return;
        }
        this.status = Status.FAILED;
    }
}
