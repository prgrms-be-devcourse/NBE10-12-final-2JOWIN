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

    /**
     * 담당 Deal id 전체 — B의 활동·할 일 집계(DB-04·05)를 SC-02 범위로 거르는 데 쓴다.
     *
     * <p><b>{@code scope == OWNED_ONLY}일 때만 호출한다.</b> 기업 관리자(COMPANY_ALL)는 회사 범위로
     * 조회하면 되고, 여기서 회사 전체 Deal id를 받으면 IN 절만 비대해진다.
     *
     * <p>소프트 삭제된 Deal은 <b>제외</b>하고, 종결(WON·LOST) Deal은 <b>포함</b>한다 —
     * 최근 활동(DB-04)에는 성사된 딜의 상담 이력도 나와야 하기 때문이다.
     * 진행 중만 필요하면 호출자가 한 번 더 거른다.
     *
     * <p>{@code companyId}를 명시적으로 받는다. 한 사람은 한 회사에만 속하므로(Q-14) memberId만으로도
     * 회사가 정해지지만, SC-01 격리는 다른 규칙에서 <b>추론</b>하지 않고 인자로 <b>명시</b>한다.
     */
    List<UUID> assignedDealIds(UUID companyId, UUID memberId);

    record DealSummary(UUID id, String title, String stage,
                       Long expectedAmount, Long wonAmount, Instant createdAt) {}
}
