package com.twojo.activity.entity;

import com.twojo.boundary.AuditActor;
import com.twojo.boundary.AuditActorType;
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

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;   // 회사 불명 이벤트(로그인)는 login_attempt 전담

    private String entityType;

    private UUID entityId;

    private String eventType;   // STAGE_MOVED 등 + 인증 6종

    @Enumerated(EnumType.STRING)
    private AuditActorType actorType;

    private UUID actorId;

    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;   // jsonb

    /**
     * 이벤트 한 건을 감사 로그 행으로 만든다 (AC-07).
     *
     * <p><b>{@link AuditActor}를 통째로 받는다.</b> {@code type()}·{@code actorId()}로 쪼개
     * 따로 넘기면, 그 값 객체가 발행 시점에 막아둔 조합 보장이 여기서 끊긴다
     * ({@code CUSTOMER_LINK}는 계정이 없어 {@code actorId}가 없어야 한다). 두 컬럼으로 푸는
     * 자리를 이 팩토리 한 곳으로 모은다.
     *
     * <p>{@code payload}는 변경된 필드만 담은 JSON이며 <b>비밀번호·토큰·해시를 넣지 않는다</b>
     * (docs/06 규약). 발행자가 실수로 담아 보낼 수 있어 적재 리스너가 한 번 거른다.
     */
    public static AuditLog of(UUID companyId, String entityType, UUID entityId, String eventType,
                              AuditActor actor, Instant occurredAt, String payload) {
        AuditLog log = new AuditLog();
        log.companyId = Objects.requireNonNull(companyId, "companyId");
        log.entityType = Objects.requireNonNull(entityType, "entityType");
        log.entityId = Objects.requireNonNull(entityId, "entityId");
        log.eventType = Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(actor, "actor");
        log.actorType = actor.type();
        log.actorId = actor.actorId();
        log.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        log.payload = payload;
        return log;
    }
}
