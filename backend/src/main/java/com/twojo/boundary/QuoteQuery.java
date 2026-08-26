package com.twojo.boundary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 견적 후보 조회 계약 — 구현: C(quote 모듈). D의 배치·대시보드가 소비한다 (v2.0.1 보강).
 * (docs/11-work-breakdown.md §4)
 */
public interface QuoteQuery {

    /** SENT·VIEWED — NT-05 리마인드 · DB-03 응답 대기 */
    List<QuoteSummary> findAwaitingResponse(UUID companyId);

    /** NT-06 임박 알림 후보 (valid_until 기준) */
    List<QuoteSummary> findExpiringUntil(LocalDate date);

    /** firstViewedAt이 null이면 미열람 (v2.0.2, GAP-08) */
    record QuoteSummary(UUID id, String quoteNo, String customerName,
                        Instant sentAt, Instant firstViewedAt, LocalDate validUntil) {}
}
