package com.twojo.quote;

import com.twojo.boundary.AuditActor;
import java.time.Instant;
import java.util.UUID;

/**
 * 고객 첫 열람 (AP-02·07) — 감사 자동 기록(AC-07)의 입력. payload 규약은 이슈 #22 3번 표다.
 *
 * <p><b>첫 열람에서만 발행한다.</b> {@code markViewed}는 멱등이라 재열람·응답 완료 뒤 열람에서는
 * 아무 일도 하지 않는데(전이표 §7), 그때도 발행하면 고객이 링크를 열 때마다 감사 로그가 쌓인다.
 *
 * <p>{@code actor}는 {@code CUSTOMER_LINK} 고정이다 — 이 경로를 부르는 것은 D의 고객 열람
 * 엔드포인트뿐이라 발행 시점에 분기가 필요 없다 (#22 3번). 계정이 없어 {@code actorId}는 null이다.
 */
public record QuoteViewed(
        UUID companyId, UUID quoteId, UUID dealId, String quoteNo,
        AuditActor actor, Instant occurredAt) {
}
