package com.twojo.boundary;

import java.util.UUID;

/**
 * 열람 링크 커맨드 계약 — 구현: D(approval 모듈). C가 호출한다 (경계 합의의 역방향).
 * <p>발급은 C의 발송 트랜잭션 안에서 <b>동기 호출</b>된다 — 링크 없는 SENT 견적은 존재하지 않는다(Q-40).
 * 메일 발송만 커밋 후 비동기. (docs/11-work-breakdown.md §5)
 */
public interface ViewTokenCommand {

    /** 발송·재발송 시 발급 — 기존 활성 링크는 EXPIRED(RESENT) */
    void issue(UUID quoteId, UUID recipientContactId);

    /** expired_reason: WITHDRAWN · MANUAL · DEAL_LOST · TIME (전이표 §7) */
    void expire(UUID quoteId, String reason);
}
