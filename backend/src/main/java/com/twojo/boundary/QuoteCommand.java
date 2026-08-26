package com.twojo.boundary;

import java.util.UUID;

/**
 * 견적 상태 변경 계약 — 구현: C(quote 모듈). D가 호출한다.
 * <p><b>D는 quote 상태를 직접 바꾸지 않는다</b> — 상태 변경 주체는 항상 C (docs/11-work-breakdown.md §4·§7.1).
 * approve·reject는 토큰 소진(AP-11)과 한 트랜잭션으로 묶인다 (D 주관).
 */
public interface QuoteCommand {

    /** GET /public/quotes/{token} 첫 열람 (AP-06·07) */
    void markViewed(UUID quoteId);

    /** AP-08·19 — 토큰 소진과 한 트랜잭션 (D 주관) */
    void approve(UUID quoteId, Responder responder);

    void reject(UUID quoteId, String reason, Responder responder);

    /** v2.0.2 — 자기 신고 신원 (Q-44), title은 null 허용 */
    record Responder(String name, String title) {}
}
