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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 현황 대시보드 조립 (DB-01~08) — boundary 계약만 엮어 응답 DTO를 만든다. 자체 테이블이 없다.
 *
 * <p><b>무트랜잭션</b> — 각 boundary 구현이 자기 readOnly 트랜잭션을 잡는다. 커넥션 하나를
 * 조립 내내 붙들지 않기 위함이며 {@code PublicQuoteAssembler}·{@code CustomerQuoteService}와 같은 방침이다.
 *
 * <p><b>C 미구현부 우회(degrade)</b> — C의 {@link SalesStatsQuery} 일부와
 * {@link QuoteQuery#findAwaitingResponse}가 {@code UnsupportedOperationException}을 던지면
 * 그 예외만 잡아 해당 섹션을 빈 값으로 채우고 200을 유지한다 — 다른 예외는 전파한다.
 * C 실구현이 머지되면 이 방어는 죽은 코드가 되어 제거한다 (issue #202).
 * B의 {@link ActivityQuery}·{@link TaskQuery}는 실 빈이 있어(#178) 그대로 호출한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final SalesStatsQuery.WonStats ZERO_WON = new SalesStatsQuery.WonStats(0L, 0);

    /** 대시보드 카드 노출 건수 — 계약 상한(50) 이하. 프론트 목 기준 10. */
    private static final int RECENT_LIMIT = 10;
    private static final int FOLLOWUP_LIMIT = 10;

    /** 실적 조회 기간 상한 — 366일(윤년 1년). 넘으면 400. */
    private static final int MAX_RANGE_DAYS = 366;

    private final SalesStatsQuery salesStatsQuery;
    private final QuoteQuery quoteQuery;
    private final ActivityQuery activityQuery;
    private final TaskQuery taskQuery;
    private final DealQuery dealQuery;

    /**
     * 요약 (DB-01~05). 스코프는 {@code ctx}로 각 협력자가 해석한다 — 단, DB-03 응답 대기는
     * {@link QuoteQuery#findAwaitingResponse}가 {@code companyId}만 받고 {@code QuoteSummary}에
     * {@code dealId}가 없어 영업 담당자 본인 필터가 불가능하다. 회사 전체 견적 노출은 데이터 누수라
     * <b>영업 담당자(OWNED_ONLY)에게는 빈 목록을 강제</b>한다 (v1 한계 — C가 {@code QuoteSummary.dealId}를
     * 추가하면 실필터로 전환, issue #202).
     *
     * <p>DB-04·05는 계약이 {@code dealId}만 주므로 제목을 {@link DealQuery#summariesByIds}로 조립한다.
     * 소프트 삭제된 딜은 결과에서 빠지므로 그 줄을 응답에서 제외한다.
     */
    public DashboardSummaryResponse summary(AccessContext ctx, YearMonth month) {
        List<DashboardSummaryResponse.StageCount> pipeline =
                orEmptyList("pipeline", () -> salesStatsQuery.pipeline(ctx)).stream()
                        .map(DashboardService::toStageCount)
                        .toList();

        SalesStatsQuery.WonStats won = orZeroWon(() -> salesStatsQuery.monthlyWon(ctx, month));

        List<DashboardSummaryResponse.WaitingQuote> waitingQuotes = ctx.scope() == AccessScope.OWNED_ONLY
                ? List.of()
                : orEmptyList("findAwaitingResponse", () -> quoteQuery.findAwaitingResponse(ctx.companyId())).stream()
                        .map(DashboardService::toWaitingQuote)
                        .toList();

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
     * 실적 분석 (DB-06~08) — <b>기업 관리자 전용</b>. 역할 자체로 갈리는 행위라 위반은 403
     * {@code FORBIDDEN}이다 (Q-43). 기간은 {@code from ≤ to}이고 {@link #MAX_RANGE_DAYS}일 이하여야
     * 하며, 벗어나면 400 {@code VALIDATION_FAILED}.
     *
     * <p>{@link SalesStatsQuery#performance}·{@link SalesStatsQuery#conversions}는 아직 자리표시자라
     * 빈 목록으로 나갈 수 있다 — 화면은 "0"이 아니라 "집계 준비 중"으로 표시한다 (C·D 협의 2026-09-08).
     */
    public DashboardPerformanceResponse performance(AccessContext ctx, LocalDate from, LocalDate to) {
        if (ctx.role() != Role.COMPANY_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        List<DashboardPerformanceResponse.MemberPerformance> members =
                orEmptyList("performance", () -> salesStatsQuery.performance(ctx.companyId(), from, to)).stream()
                        .map(DashboardService::toMemberPerformance)
                        .toList();

        List<DashboardPerformanceResponse.StageConversion> conversions =
                orEmptyList("conversions", () -> salesStatsQuery.conversions(ctx.companyId(), from, to)).stream()
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

    /** throw 스텁이면 빈 목록. {@code UnsupportedOperationException}만 삼킨다 — 다른 예외는 전파한다. */
    private static <T> List<T> orEmptyList(String label, Supplier<List<T>> call) {
        try {
            return call.get();
        } catch (UnsupportedOperationException notReady) {
            log.warn("집계 계약 미구현 - {} 섹션을 빈 목록으로 응답한다 (C 실구현 대기)", label);
            return List.of();
        }
    }

    /** throw 스텁이면 {@code (0, 0)}. */
    private static SalesStatsQuery.WonStats orZeroWon(Supplier<SalesStatsQuery.WonStats> call) {
        try {
            return call.get();
        } catch (UnsupportedOperationException notReady) {
            log.warn("집계 계약 미구현 - 이달 성사를 0으로 응답한다 (C 실구현 대기)");
            return ZERO_WON;
        }
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
