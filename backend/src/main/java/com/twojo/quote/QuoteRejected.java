package com.twojo.quote;

import com.twojo.boundary.AuditActor;
import java.time.Instant;
import java.util.UUID;

/**
 * 고객 반려 (AP-09·10·19) — 감사 자동 기록(AC-07)의 입력. payload 규약은 이슈 #22 3번 표다.
 *
 * <p>{@code actor}는 {@code CUSTOMER_LINK} 고정, {@code actorId}는 null이다 (#22 3번).
 *
 * @param reason        반려 사유 (AP-10). 발생 전에 존재할 수 없는 값이라 {@code changes}가 아니라
 *                      부가 필드다 — {@code before}가 예외 없이 비기 때문이다 (#22 2번)
 * @param responderName 검증되지 않은 자기 신고 (AP-19 · Q-44) — {@link QuoteApproved}와 같다
 */
public record QuoteRejected(
        UUID companyId, UUID quoteId, UUID dealId, String quoteNo,
        String responderName, String reason,
        AuditActor actor, Instant occurredAt) {
}
