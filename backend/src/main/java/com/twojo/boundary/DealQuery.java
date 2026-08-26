package com.twojo.boundary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deal 조회 계약 — 구현: C(deal 모듈). B·D는 deal 테이블을 직접 조회하지 않는다.
 * (docs/11-work-breakdown.md §4)
 */
public interface DealQuery {

    /** D 알림 수신자 결정(Q-26) · B 접근 판정 */
    UUID assigneeIdOf(UUID dealId);

    /** 진행 중(리드~협상) 여부 */
    boolean isOpen(UUID dealId);

    /** B의 CU-08 판정 — 고객사 삭제 차단 (v2.0.1 보강) */
    boolean hasOpenDeals(UUID customerId);

    /** B의 CU-12 — 고객사 상세 Deal 이력 (v2.0.1 보강) */
    List<DealSummary> summariesByCustomer(UUID customerId);

    record DealSummary(UUID id, String title, String stage,
                       Long expectedAmount, Long wonAmount, Instant createdAt) {}
}
