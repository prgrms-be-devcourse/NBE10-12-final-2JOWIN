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
 * 인앱 알림 (Q-23 폴링 30초) — 복합 FK(company_id, recipient_member_id)로 교차 테넌트 유출 차단.
 * 수신자 = 발송 시점의 유효한 담당자, 비활성이면 기업 관리자 폴백 (Q-26). 읽은 알림 90일 후 삭제 배치.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    /** NT-03~05·10·12 — EMAIL_FAILED는 인앱 전용, NT-07로 끌 수 없음 (Q-35) */
    public enum Type {
        QUOTE_VIEWED, QUOTE_APPROVED, QUOTE_REJECTED, REMIND_NO_RESPONSE, INQUIRY_RECEIVED, EMAIL_FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private UUID recipientMemberId;

    @Enumerated(EnumType.STRING)
    private Type type;

    private String message;

    private String refType;   // 클릭 이동 대상

    private UUID refId;

    private Instant readAt;   // null = 안 읽음

    /** 알림 생성 — 미읽음(readAt=null) 상태. refType·refId는 클릭 이동 대상이 없으면 null. */
    public static Notification of(UUID companyId, UUID recipientMemberId, Type type,
                                  String message, String refType, UUID refId) {
        Notification notification = new Notification();
        notification.companyId = Objects.requireNonNull(companyId, "companyId");
        notification.recipientMemberId = Objects.requireNonNull(recipientMemberId, "recipientMemberId");
        notification.type = Objects.requireNonNull(type, "type");
        notification.message = Objects.requireNonNull(message, "message");
        notification.refType = refType;
        notification.refId = refId;
        return notification;
    }

    /** 읽음 처리 — 최초 1회만 시각 기록, 이후 호출은 무동작(멱등). (NT-08) */
    public void markRead() {
        if (readAt == null) {
            this.readAt = Instant.now();
        }
    }
}
