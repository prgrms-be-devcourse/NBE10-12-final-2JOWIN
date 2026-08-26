package com.twojo.activity.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 감사 로그 (AC-07·11) — 각 도메인이 이벤트를 발행하고 B의 리스너가 적재한다.
 * payload 규약: 변경된 필드만 {"field": {"before": .., "after": ..}} · 비밀번호·토큰·해시 저장 금지
 * · 견적·주문 이벤트에는 dealId 필수 (AC-06 타임라인 병합 키).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseTimeEntity {

    public enum ActorType { MEMBER, PLATFORM_ADMIN, CUSTOMER_LINK, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;   // 회사 불명 이벤트(로그인)는 login_attempt 전담

    private String entityType;

    private UUID entityId;

    private String eventType;   // STAGE_MOVED 등 + 인증 6종

    @Enumerated(EnumType.STRING)
    private ActorType actorType;

    private UUID actorId;

    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;   // jsonb
}
