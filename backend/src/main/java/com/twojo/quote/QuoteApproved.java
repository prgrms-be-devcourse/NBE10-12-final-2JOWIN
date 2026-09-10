package com.twojo.quote;

import com.twojo.boundary.AuditActor;
import java.time.Instant;
import java.util.UUID;

/**
 * 고객 승인 (AP-08·19) — 감사 자동 기록(AC-07)의 입력. payload 규약은 이슈 #22 3번 표다.
 *
 * <p>{@code actor}는 {@code CUSTOMER_LINK} 고정, {@code actorId}는 null이다 (#22 3번).
 *
 * @param responderName <b>검증되지 않은 자기 신고</b>다 (AP-19 · Q-44). 계정 없는 고객이 직접
 *                      밝힌 이름이라 타임라인에 인증된 신원처럼 표시하면 안 된다.
 *                      {@code quote}에도 저장되지만 B가 그 테이블을 읽을 수 없어 payload로 받는다
 */
public record QuoteApproved(
        UUID companyId, UUID quoteId, UUID dealId, String quoteNo,
        String responderName,
        AuditActor actor, Instant occurredAt) {
}
