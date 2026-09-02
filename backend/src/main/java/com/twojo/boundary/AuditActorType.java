package com.twojo.boundary;

/**
 * 감사 로그 행위자 유형 — {@code audit_log.actor_type}의 CHECK 값과 1:1이다.
 * <b>값을 바꾸면 마이그레이션도 함께 가야 한다.</b>
 *
 * <p><b>{@code actorId}와의 짝은 {@link AuditActor}가 발행 시점에 강제한다.</b>
 * {@link #MEMBER}·{@link #PLATFORM_ADMIN}은 {@code actor_id}가 있어야 하고
 * {@link #CUSTOMER_LINK}·{@link #SYSTEM}은 없어야 한다. 이벤트 페이로드에는
 * 이 enum을 직접 싣지 말고 {@link AuditActor}로 싣는다.
 */
public enum AuditActorType {

    /** 구성원의 조작. {@code actorId} = member.id */
    MEMBER,

    /** 플랫폼 관리자의 회사 대상 조작 — 회사 정지·해제 등. {@code actorId} = platform_admin.id */
    PLATFORM_ADMIN,

    /** 고객이 열람 링크로 한 응답 — 계정이 없어 {@code actorId}가 없다 */
    CUSTOMER_LINK,

    /** 사람이 아닌 전이 — 배치·자동 전이. {@code actorId}가 없다 */
    SYSTEM
}
