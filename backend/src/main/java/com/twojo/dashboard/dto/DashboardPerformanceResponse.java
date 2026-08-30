package com.twojo.dashboard.dto;

import java.util.List;
import java.util.UUID;

/** 담당자별 실적·단계 전환율 (DB-06~08) — 기업 관리자 전용. */
public record DashboardPerformanceResponse(
        List<MemberPerformance> members,           // DB-06·07
        List<StageConversion> conversions) {       // DB-08

    public record MemberPerformance(UUID memberId, String name,
                                    int wonCount, Long wonAmount, int activeDealCount) {}

    public record StageConversion(String fromStage, String toStage, double rate) {}
}
