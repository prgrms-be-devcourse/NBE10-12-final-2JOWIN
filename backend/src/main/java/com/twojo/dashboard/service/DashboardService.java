package com.twojo.dashboard.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.ActivityQuery;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.Role;
import com.twojo.boundary.SalesStatsQuery;
import com.twojo.boundary.TaskQuery;
import com.twojo.dashboard.dto.DashboardPerformanceResponse;
import com.twojo.dashboard.dto.DashboardSummaryResponse;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 현황 대시보드 조립 (DB-01~08) — boundary 계약만 엮어 응답 DTO를 만든다. 자체 테이블이 없다.
 *
 * <p><b>무트랜잭션</b> — 각 boundary 구현이 자기 readOnly 트랜잭션을 잡는다. 커넥션 하나를
 * 조립 내내 붙들지 않기 위함이며 {@code PublicQuoteAssembler}·{@code CustomerQuoteService}와 같은 방침이다.
 *
 * <p><b>{@link SalesStatsQuery#conversions}만 아직 자리표시자다</b> — 빈 목록을 돌려준다.
 * 단계 전환율은 {@code audit_log}의 {@code STAGE_MOVED} 적재가 선행인데 그 리스너가 아직 없다.
 * 나머지는 전부 실구현이다 — {@code pipeline}·{@link SalesStatsQuery#monthlyWon}·
 * {@link SalesStatsQuery#performance}(#216)와 {@link QuoteQuery#findAwaitingResponse}.
 *
 * <p><b>백엔드가 실값을 내는 것과 화면에 뜨는 것은 다르다.</b> 프론트의 {@code SALES_STATS_PENDING}이
 * 아직 켜져 있어 이달 성사·담당자별 실적 카드는 "집계 준비 중"으로 남는다 — 해제는 #283이다.
 * 전환율 카드는 그 뒤에도 안내 문구를 유지한다 (2026-09-10 D 확인) — 리스너가 붙기 전의 전이는
 * 남지 않아 초기 수치가 실제와 어긋나기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 대시보드 카드 노출 건수 — 계약 상한(50) 이하. 프론트 목 기준 10. */
    private static final int RECENT_LIMIT = 10;
    private static final int FOLLOWUP_LIMIT = 10;

    /** 실적 조회 기간 상한 — from~to 간격이 366일(윤년 1년) 이상이면 400. */
    private static final int MAX_RANGE_DAYS = 366;

    private final SalesStatsQuery salesStatsQuery;
    private final QuoteQuery quoteQuery;
    private final ActivityQuery activityQuery;
    private final TaskQuery taskQuery;
    private final DealQuery dealQuery;

    /**
     * 요약 (DB-01~05). 스코프는 대부분 {@code ctx}로 각 협력자가 해석한다 — 단, DB-03 응답 대기는
     * {@link QuoteQuery#findAwaitingResponse}가 회사 전체를 돌려주므로 <b>영업 담당자(OWNED_ONLY)는
     * {@link DealQuery#assignedDealIds}로 본인 담당 딜 견적만 남긴다</b> (SC-02).
     *
     * <p>DB-04·05는 계약이 {@code dealId}만 주므로 제목을 {@link DealQuery#summariesByIds}로 조립한다.
     * 소프트 삭제된 딜은 결과에서 빠지므로 그 줄을 응답에서 제외한다.
     */
    public DashboardSummaryResponse summary(AccessContext ctx, YearMonth month) {
        List<DashboardSummaryResponse.StageCount> pipeline = salesStatsQuery.pipeline(ctx).stream()
                .map(DashboardService::toStageCount)
                .toList();

        SalesStatsQuery.WonStats won = salesStatsQuery.monthlyWon(ctx, month);

        List<DashboardSummaryResponse.WaitingQuote> waitingQuotes = waitingQuotes(ctx);

        List<ActivityQuery.RecentActivitySummary> activities = activityQuery.recent(ctx, RECENT_LIMIT);
        List<TaskQuery.FollowUpSummary> tasks = taskQuery.followUps(ctx, FOLLOWUP_LIMIT);
        Map<UUID, String> dealTitles = dealTitles(ctx.companyId(), activities, tasks);

        List<DashboardSummaryResponse.RecentActivity> recentActivities = activities.stream()
                .filter(a -> dealTitles.containsKey(a.dealId()))
                .map(a -> new DashboardSummaryResponse.RecentActivity(
                        a.dealId(), dealTitles.get(a.dealId()), a.summary(), a.occurredAt()))
                .toList();

        List<DashboardSummaryResponse.FollowUp> followUps = tasks.stream()
                .filter(t -> dealTitles.containsKey(t.dealId()))
                .map(t -> new DashboardSummaryResponse.FollowUp(
                        t.taskId(), t.dealId(), dealTitles.get(t.dealId()), t.content(), t.dueDate()))
                .toList();

        return new DashboardSummaryResponse(
                pipeline, won.amount(), won.count(), waitingQuotes, followUps, recentActivities);
    }

    /**
     * DB-03 응답 대기. {@link QuoteQuery#findAwaitingResponse}는 {@code AccessContext}를 받지 못해
     * 회사 전체를 돌려주므로, 영업 담당자는 {@link DealQuery#assignedDealIds}로 본인 담당 딜만 남긴다.
     */
    private List<DashboardSummaryResponse.WaitingQuote> waitingQuotes(AccessContext ctx) {
        List<QuoteQuery.QuoteSummary> awaiting = quoteQuery.findAwaitingResponse(ctx.companyId());
        if (ctx.scope() == AccessScope.OWNED_ONLY) {
            Set<UUID> ownedDeals = Set.copyOf(dealQuery.assignedDealIds(ctx.companyId(), ctx.memberId()));
            awaiting = awaiting.stream().filter(q -> ownedDeals.contains(q.dealId())).toList();
        }
        return awaiting.stream().map(DashboardService::toWaitingQuote).toList();
    }

    /**
     * 실적 분석 (DB-06~08) — <b>기업 관리자 전용</b>. 역할 위반은 403 {@code FORBIDDEN} (Q-43).
     * 기간은 {@code from <= to}이고 간격이 {@link #MAX_RANGE_DAYS}일 미만이어야 하며, 벗어나면 400 {@code VALIDATION_FAILED}.
     * {@code conversions}만 아직 자리표시자라 빈 목록이다 — {@code members}는 실집계다 (위 클래스 주석).
     */
    public DashboardPerformanceResponse performance(AccessContext ctx, LocalDate from, LocalDate to) {
        if (ctx.role() != Role.COMPANY_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        List<DashboardPerformanceResponse.MemberPerformance> members =
                salesStatsQuery.performance(ctx.companyId(), from, to).stream()
                        .map(DashboardService::toMemberPerformance)
                        .toList();

        List<DashboardPerformanceResponse.StageConversion> conversions =
                salesStatsQuery.conversions(ctx.companyId(), from, to).stream()
                        .map(DashboardService::toStageConversion)
                        .toList();

        return new DashboardPerformanceResponse(members, conversions);
    }

    /**
     * DB-04·05 줄마다 붙는 딜 제목 — 두 목록의 {@code dealId}를 합쳐 한 번에 조회한다.
     * 소프트 삭제된 딜은 {@link DealQuery#summariesByIds} 결과에서 빠지므로 호출부가 그 줄을 제외한다.
     */
    private Map<UUID, String> dealTitles(UUID companyId,
                                         List<ActivityQuery.RecentActivitySummary> activities,
                                         List<TaskQuery.FollowUpSummary> tasks) {
        Set<UUID> dealIds = new LinkedHashSet<>();
        activities.forEach(a -> dealIds.add(a.dealId()));
        tasks.forEach(t -> dealIds.add(t.dealId()));
        if (dealIds.isEmpty()) {
            return Map.of();
        }
        return dealQuery.summariesByIds(companyId, dealIds).stream()
                .collect(Collectors.toMap(
                        DealQuery.DealSummary::id, DealQuery.DealSummary::title, (a, b) -> a));
    }

    private static DashboardSummaryResponse.StageCount toStageCount(SalesStatsQuery.StageCount s) {
        return new DashboardSummaryResponse.StageCount(s.stage(), s.count(), s.expectedAmountSum());
    }

    private static DashboardSummaryResponse.WaitingQuote toWaitingQuote(QuoteQuery.QuoteSummary q) {
        return new DashboardSummaryResponse.WaitingQuote(
                q.id(), q.quoteNo(), q.customerName(), q.sentAt(), q.firstViewedAt(), q.validUntil());
    }

    private static DashboardPerformanceResponse.MemberPerformance toMemberPerformance(
            SalesStatsQuery.MemberPerformance m) {
        return new DashboardPerformanceResponse.MemberPerformance(
                m.memberId(), m.name(), m.wonCount(), m.wonAmount(), m.activeDealCount());
    }

    private static DashboardPerformanceResponse.StageConversion toStageConversion(
            SalesStatsQuery.StageConversion c) {
        return new DashboardPerformanceResponse.StageConversion(c.fromStage(), c.toStage(), c.rate());
    }
}
