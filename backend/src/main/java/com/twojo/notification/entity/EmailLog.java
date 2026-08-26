package com.twojo.notification.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
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
}
