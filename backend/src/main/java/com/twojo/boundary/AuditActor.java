package com.twojo.boundary;

import java.util.Objects;
import java.util.UUID;

/**
 * 감사 로그 행위자 — {@link AuditActorType}과 {@code actorId}를 한 값으로 묶는다.
 *
 * <p><b>조합 불변식은 이 타입이 발행 시점에 강제한다.</b> {@code MEMBER}·{@code PLATFORM_ADMIN}은
 * {@code actorId}가 있어야 하고 {@code CUSTOMER_LINK}·{@code SYSTEM}은 없어야 하는데,
 * {@code audit_log.actor_id}가 NULL 허용이라 DB는 잘못된 조합도 저장한다.
 * 잘못된 조합은 발행자가 이벤트를 만드는 순간 — 즉 업무 트랜잭션 안에서 — 예외로 터진다.
 * 적재 시점(비동기 리스너·DB CHECK)에 걸러내면 업무 행위는 이미 커밋된 뒤라
 * "행위는 있고 기록은 없는" 상태가 되지만, 발행 시점에 터지면 행위 자체가 실패하므로
 * 그 상태가 생기지 않는다. 정상 경로에서는 정적 팩토리 4개만 쓴다.
 */
public record AuditActor(AuditActorType type, UUID actorId) {

    public AuditActor {
        Objects.requireNonNull(type, "type");
        boolean requiresId = (type == AuditActorType.MEMBER || type == AuditActorType.PLATFORM_ADMIN);
        if (requiresId && actorId == null) {
            throw new IllegalArgumentException(type + "는 actorId가 필요하다");
        }
        if (!requiresId && actorId != null) {
            throw new IllegalArgumentException(type + "는 actorId를 가질 수 없다: " + actorId);
        }
    }

    /** 구성원의 조작. {@code actorId} = member.id */
    public static AuditActor member(UUID memberId) {
        return new AuditActor(AuditActorType.MEMBER, memberId);
    }

    /** 플랫폼 관리자의 회사 대상 조작. {@code actorId} = platform_admin.id */
    public static AuditActor platformAdmin(UUID platformAdminId) {
        return new AuditActor(AuditActorType.PLATFORM_ADMIN, platformAdminId);
    }

    /** 고객이 열람 링크로 한 응답 — 계정이 없어 {@code actorId}가 없다 */
    public static AuditActor customerLink() {
        return new AuditActor(AuditActorType.CUSTOMER_LINK, null);
    }

    /** 사람이 아닌 전이 — 배치·자동 전이 */
    public static AuditActor system() {
        return new AuditActor(AuditActorType.SYSTEM, null);
    }
}
