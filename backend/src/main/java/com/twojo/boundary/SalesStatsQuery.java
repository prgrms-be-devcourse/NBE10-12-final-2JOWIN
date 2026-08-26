package com.twojo.boundary;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * 대시보드 집계 계약 — 구현: C. D가 소비한다 (DB-01~08, v2.0.1 보강. SC 범위는 ctx로 적용).
 * <p>집계도 이 인터페이스 경유가 원칙 — deal·quote·orders 직접 조회 금지.
 * 성능 문제가 확인되면 읽기 전용 뷰 허용 여부를 팀 합의로 결정한다. (docs/11-work-breakdown.md §4)
 */
public interface SalesStatsQuery {

    /** DB-01 — 진행 단계(리드~협상) 기준 */
    List<StageCount> pipeline(AccessContext ctx);

    /** DB-02 — 주문 합계 (DL-18) */
    WonStats monthlyWon(AccessContext ctx, YearMonth month);

    /** DB-06·08 — 기업 관리자 전용 */
    List<MemberPerformance> performance(UUID companyId, LocalDate from, LocalDate to);

    /** DB-07 */
    List<StageConversion> conversions(UUID companyId, LocalDate from, LocalDate to);

    record StageCount(String stage, int count, Long expectedAmountSum) {}

    record WonStats(Long amount, int count) {}

    record MemberPerformance(UUID memberId, String name,
                             int wonCount, Long wonAmount, int activeDealCount) {}

    record StageConversion(String fromStage, String toStage, double rate) {}
}
