package com.twojo.activity.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 감사 로그 상세 응답 — AC-11 "무엇을 변경했는지"의 실체다.
 *
 * <p>{@code changes}는 <b>변경된 필드만</b> 담는다. 안 바뀐 필드는 키 자체가 없다.
 * {@code QuoteSent}처럼 없던 일이 생긴 발생형 이벤트는 before가 없어 이 키가 통째로 빠진다.
 *
 * <pre>{@code
 * { "stage": { "before": "NEGOTIATION", "after": "WON" } }
 * }</pre>
 *
 * <p>payload에 <b>비밀번호·토큰·해시를 저장하지 않는다</b> (docs/06 규약).
 * 발행자가 실수로 담아 보낼 수 있으므로 적재 리스너에서 한 번 거른다.
 */
public record AuditLogDetailResponse(
        UUID id,
        String entityType,
        UUID entityId,
        String eventType,
        String actorType,
        UUID actorId,
        String actorName,
        Map<String, FieldChange> changes,
        Instant occurredAt) {

    /** 필드 하나의 변경 전후. 타입이 제각각이라 {@code Object}로 받는다. */
    public record FieldChange(Object before, Object after) {}
}
