package com.twojo.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 현황 대시보드 요약 (DB-01~05). 영업 담당자는 본인 담당 Deal 기준 집계 (SC-02).
 * pipeline은 진행 단계(리드~협상)만 — WON 금액은 monthWonAmount(주문 합계, DL-18), LOST 제외.
 */
public record DashboardSummaryResponse(
        List<StageCount> pipeline,                 // DB-01 단계별 건수·금액
        Long monthWonAmount,                       // DB-02 이달 성사 = 주문 합계
        int monthWonCount,
        List<WaitingQuote> waitingQuotes,          // DB-03 응답 대기
        List<FollowUp> followUps,                  // DB-05 후속 필요
        List<RecentActivity> recentActivities) {   // DB-04 최근 활동

    public record StageCount(String stage, int count, Long expectedAmountSum) {}

    public record WaitingQuote(UUID quoteId, String quoteNo, String customerName,
                               Instant sentAt,
                               Instant firstViewedAt,   // null이면 미열람 (AP-06, GAP-08)
                               LocalDate validUntil) {}

    public record FollowUp(UUID taskId, UUID dealId, String dealTitle,
                           String content, LocalDate dueDate) {}

    public record RecentActivity(UUID dealId, String dealTitle, String summary, Instant occurredAt) {}
}
