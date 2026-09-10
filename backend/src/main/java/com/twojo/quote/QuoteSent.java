package com.twojo.quote;

import com.twojo.boundary.AuditActor;
import java.time.Instant;
import java.util.UUID;

/**
 * 견적 발송 (QT-13) — 감사 자동 기록(AC-07)의 입력. payload 규약은 이슈 #22 3번 표다.
 *
 * <p><b>발생형 이벤트다</b> — 없던 일이 생긴 것이라 {@code before}에 넣을 값이 없다.
 * 그래서 B의 payload에 {@code changes} 키가 들어가지 않고 {@code quoteNo}만 부가 필드로 실린다 (#22 2번).
 *
 * <p><b>토큰·URL을 싣지 않는다</b> (11 §5 269행, #22 5번) — 열람 링크 원문이 감사 로그에 남으면
 * 로그를 볼 수 있는 사람이 고객 견적을 열고 승인까지 할 수 있다.
 *
 * @param dealId 필수 — AC-06 딜 타임라인 병합 키다. 없으면 자동 기록이 딜 상세에 붙지 않는다
 */
public record QuoteSent(
        UUID companyId, UUID quoteId, UUID dealId, String quoteNo,
        AuditActor actor, Instant occurredAt) {
}
