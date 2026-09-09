package com.twojo.boundary;

import java.util.Optional;
import java.util.UUID;

/**
 * 열람 링크 조회 계약 — 구현: D(approval 모듈). B가 {@link #existsForContact}를,
 * notification 모듈(NT-12)이 {@link #quoteIdOf}를 호출한다 (v2.0.13).
 * (docs/11-work-breakdown.md §5)
 */
public interface ViewTokenQuery {

    /** 발송 이력(수신인 지정 이력) 존재 — true면 CU-14 CONTACT_HAS_QUOTES로 삭제 차단 */
    boolean existsForContact(UUID contactId);

    /**
     * 열람 링크 토큰 id &rarr; 그 링크가 가리키는 견적 id.
     *
     * <p>NT-12에서 쓴다 — 최종 실패한 {@code QUOTE_SENT} 메일의 {@code email_log.ref_id}가 발송 토큰 id라,
     * 담당 구성원을 찾으려면 먼저 견적으로 되짚어야 한다. {@code quote_view_token}엔 {@code deal_id}가 없어
     * 이후 {@code QuoteQuery.getPublicView}로 딜까지 간다.
     *
     * <p><b>토큰 행이 없으면 예외가 아니라 {@code Optional.empty()}</b> — 부가 로직(알림)이 메인 흐름에
     * 스택트레이스를 흘리지 않도록 호출자가 조용히 건너뛴다.
     */
    Optional<UUID> quoteIdOf(UUID tokenId);
}
