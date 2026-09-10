package com.twojo.order;

import com.twojo.boundary.AuditActor;
import java.time.Instant;
import java.util.UUID;

/**
 * 주문 전환 (OD-01) — 감사 자동 기록(AC-07)의 입력. payload 규약은 이슈 #22 3번 표다.
 *
 * <p><b>같은 트랜잭션에서 {@code DealStageChanged}(자동 성사)가 함께 나간다</b> (OD-06) —
 * 타임라인에 "주문 생성"과 "성사" 두 줄이 나란히 선다. 행위자가 다르다: 전환은 구성원이 누른
 * 것이라 {@code MEMBER}이고, 성사는 그 부수 효과라 {@code SYSTEM}이다 (#22 3번).
 *
 * <p><b>발생형 이벤트다</b> — {@code changes} 키 없이 {@code orderNo}·{@code quoteId}만 부가 필드로 실린다.
 *
 * @param dealId 필수 — {@code orders}에 {@code deal_id} 컬럼이 없어 견적을 거쳐 얻는다 (AC-06 병합 키)
 */
public record OrderCreated(
        UUID companyId, UUID orderId, UUID dealId, String orderNo, UUID quoteId,
        AuditActor actor, Instant occurredAt) {
}
