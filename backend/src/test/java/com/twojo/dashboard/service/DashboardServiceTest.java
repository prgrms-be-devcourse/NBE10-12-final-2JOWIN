package com.twojo.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.ActivityQuery;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.Role;
import com.twojo.boundary.SalesStatsQuery;
import com.twojo.boundary.TaskQuery;
import com.twojo.dashboard.dto.DashboardSummaryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DashboardService#summary} — 집계 3섹션(DB-01·02·03) 매핑과 C 미구현 degrade,
 * 영업 담당자의 응답 대기 강제 빈 목록, DB-04·05의 딜 제목 조립과 소프트 삭제 행 제외를 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DashboardServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("1e000000-0000-4000-8000-000000000002");
    private static final UUID DEAL_A = UUID.fromString("5d000000-0000-4000-8000-00000000000a");
    private static final UUID DEAL_B = UUID.fromString("5d000000-0000-4000-8000-00000000000b");
    private static final UUID DEAL_GONE = UUID.fromString("5d000000-0000-4000-8000-0000000000ff");
    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    @Mock
    private SalesStatsQuery salesStatsQuery;
    @Mock
    private QuoteQuery quoteQuery;
    @Mock
    private ActivityQuery activityQuery;
    @Mock
    private TaskQuery taskQuery;
    @Mock
    private DealQuery dealQuery;
    @InjectMocks
    private DashboardService dashboardService;

    private static AccessContext ctx(AccessScope scope) {
        Role role = scope == AccessScope.COMPANY_ALL ? Role.COMPANY_ADMIN : Role.SALES_REP;
        return new AccessContext(COMPANY_ID, MEMBER_ID, role, scope);
    }

    private static SalesStatsQuery.StageCount stage(String name, int count, long sum) {
        return new SalesStatsQuery.StageCount(name, count, sum);
    }

    private static QuoteQuery.QuoteSummary awaiting(String quoteNo) {
        return new QuoteQuery.QuoteSummary(UUID.randomUUID(), quoteNo, "도담건설",
                Instant.parse("2026-09-01T00:00:00Z"), null, LocalDate.of(2026, 9, 30));
    }

    private static ActivityQuery.RecentActivitySummary act(UUID dealId, String summary) {
        return new ActivityQuery.RecentActivitySummary(dealId, summary, Instant.parse("2026-09-05T00:00:00Z"));
    }

    private static TaskQuery.FollowUpSummary task(UUID taskId, UUID dealId, String content) {
        return new TaskQuery.FollowUpSummary(taskId, dealId, content, LocalDate.of(2026, 9, 10));
    }

    private static DealQuery.DealSummary deal(UUID id, String title) {
        return new DealQuery.DealSummary(id, title, "QUOTE", 1_000_000L, null, Instant.parse("2026-08-01T00:00:00Z"));
    }

    /** DB-01·02·03 섹션을 빈 값으로 stub — DB-04·05만 보는 테스트용. */
    private void givenEmptyAggregates() {
        given(salesStatsQuery.pipeline(any())).willReturn(List.of());
        given(salesStatsQuery.monthlyWon(any(), any())).willReturn(new SalesStatsQuery.WonStats(0L, 0));
        given(quoteQuery.findAwaitingResponse(COMPANY_ID)).willReturn(List.of());
    }

    @Test
    @DisplayName("summary는 pipeline·이달 성사·응답 대기를 계약 결과로 채운다")
    void summary는_세_섹션을_채운다() {
        given(salesStatsQuery.pipeline(any()))
                .willReturn(List.of(stage("LEAD", 4, 180_000L), stage("QUOTE", 5, 1_200_000L)));
        given(salesStatsQuery.monthlyWon(any(), eq(MONTH)))
                .willReturn(new SalesStatsQuery.WonStats(48_500_000L, 3));
        given(quoteQuery.findAwaitingResponse(COMPANY_ID))
                .willReturn(List.of(awaiting("Q-2609-001")));

        DashboardSummaryResponse res = dashboardService.summary(ctx(AccessScope.COMPANY_ALL), MONTH);

        assertThat(res.pipeline())
                .extracting(DashboardSummaryResponse.StageCount::stage)
                .containsExactly("LEAD", "QUOTE");
        assertThat(res.monthWonAmount()).isEqualTo(48_500_000L);
        assertThat(res.monthWonCount()).isEqualTo(3);
        assertThat(res.waitingQuotes())
                .extracting(DashboardSummaryResponse.WaitingQuote::quoteNo)
                .containsExactly("Q-2609-001");
    }

    @Test
    @DisplayName("SalesStatsQuery가 미구현이면 pipeline은 빈 목록, 이달 성사는 0이다")
    void 집계_스텁이면_빈_값으로_degrade한다() {
        given(salesStatsQuery.pipeline(any()))
                .willThrow(new UnsupportedOperationException("SalesStatsQuery.pipeline - C 3주차 구현 예정"));
        given(salesStatsQuery.monthlyWon(any(), any()))
                .willThrow(new UnsupportedOperationException("SalesStatsQuery.monthlyWon - C 3주차 구현 예정"));
        given(quoteQuery.findAwaitingResponse(COMPANY_ID)).willReturn(List.of());

        DashboardSummaryResponse res = dashboardService.summary(ctx(AccessScope.COMPANY_ALL), MONTH);

        assertThat(res.pipeline()).isEmpty();
        assertThat(res.monthWonAmount()).isEqualTo(0L);
        assertThat(res.monthWonCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("findAwaitingResponse가 미구현이면 응답 대기는 빈 목록이다")
    void 응답대기_스텁이면_빈_목록이다() {
        given(salesStatsQuery.pipeline(any())).willReturn(List.of());
        given(salesStatsQuery.monthlyWon(any(), any())).willReturn(new SalesStatsQuery.WonStats(0L, 0));
        given(quoteQuery.findAwaitingResponse(COMPANY_ID))
                .willThrow(new UnsupportedOperationException("QuoteQuery.findAwaitingResponse - C 3주차 구현 예정"));

        DashboardSummaryResponse res = dashboardService.summary(ctx(AccessScope.COMPANY_ALL), MONTH);

        assertThat(res.waitingQuotes()).isEmpty();
    }

    @Test
    @DisplayName("영업 담당자는 응답 대기가 강제로 빈 목록이고 findAwaitingResponse를 부르지 않는다")
    void 영업담당자는_응답대기가_강제로_빈_목록이다() {
        given(salesStatsQuery.pipeline(any())).willReturn(List.of());
        given(salesStatsQuery.monthlyWon(any(), any())).willReturn(new SalesStatsQuery.WonStats(0L, 0));

        DashboardSummaryResponse res = dashboardService.summary(ctx(AccessScope.OWNED_ONLY), MONTH);

        assertThat(res.waitingQuotes()).isEmpty();
        verify(quoteQuery, never()).findAwaitingResponse(any());
    }

    @Test
    @DisplayName("AccessContext는 pipeline·monthlyWon에 그대로 전달된다")
    void ctx가_집계_계약에_그대로_전달된다() {
        AccessContext ctx = ctx(AccessScope.COMPANY_ALL);
        given(salesStatsQuery.pipeline(any())).willReturn(List.of());
        given(salesStatsQuery.monthlyWon(any(), any())).willReturn(new SalesStatsQuery.WonStats(0L, 0));
        given(quoteQuery.findAwaitingResponse(COMPANY_ID)).willReturn(List.of());

        dashboardService.summary(ctx, MONTH);

        verify(salesStatsQuery).pipeline(ctx);
        verify(salesStatsQuery).monthlyWon(ctx, MONTH);
    }

    @Test
    @DisplayName("최근 활동·후속 필요에 딜 제목을 붙인다")
    void DB04_05에_딜_제목을_붙인다() {
        givenEmptyAggregates();
        given(activityQuery.recent(any(), eq(10))).willReturn(List.of(act(DEAL_A, "전화 상담")));
        given(taskQuery.followUps(any(), eq(10))).willReturn(List.of(task(UUID.randomUUID(), DEAL_B, "재방문")));
        given(dealQuery.summariesByIds(eq(COMPANY_ID), any()))
                .willReturn(List.of(deal(DEAL_A, "도담건설 리모델링"), deal(DEAL_B, "성원산업 정기납품")));

        DashboardSummaryResponse res = dashboardService.summary(ctx(AccessScope.COMPANY_ALL), MONTH);

        assertThat(res.recentActivities())
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.dealTitle()).isEqualTo("도담건설 리모델링");
                    assertThat(a.summary()).isEqualTo("전화 상담");
                });
        assertThat(res.followUps())
                .singleElement()
                .satisfies(f -> assertThat(f.dealTitle()).isEqualTo("성원산업 정기납품"));
    }

    @Test
    @DisplayName("제목을 못 찾은 딜(소프트 삭제)의 행은 제외한다")
    void 제목_없는_딜의_행은_제외한다() {
        givenEmptyAggregates();
        given(activityQuery.recent(any(), eq(10)))
                .willReturn(List.of(act(DEAL_A, "살아있는 딜"), act(DEAL_GONE, "삭제된 딜")));
        given(taskQuery.followUps(any(), eq(10))).willReturn(List.of());
        given(dealQuery.summariesByIds(eq(COMPANY_ID), any()))
                .willReturn(List.of(deal(DEAL_A, "도담건설 리모델링")));

        DashboardSummaryResponse res = dashboardService.summary(ctx(AccessScope.COMPANY_ALL), MONTH);

        assertThat(res.recentActivities())
                .extracting(DashboardSummaryResponse.RecentActivity::dealId)
                .containsExactly(DEAL_A);
    }

    @Test
    @DisplayName("두 목록의 dealId를 한 번의 summariesByIds로 조회한다")
    void 딜_제목은_한_번에_배치_조회한다() {
        givenEmptyAggregates();
        given(activityQuery.recent(any(), eq(10))).willReturn(List.of(act(DEAL_A, "상담")));
        given(taskQuery.followUps(any(), eq(10))).willReturn(List.of(task(UUID.randomUUID(), DEAL_B, "할 일")));
        given(dealQuery.summariesByIds(eq(COMPANY_ID), any())).willReturn(List.of());

        dashboardService.summary(ctx(AccessScope.COMPANY_ALL), MONTH);

        verify(dealQuery).summariesByIds(eq(COMPANY_ID),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(DEAL_A, DEAL_B))));
    }

    @Test
    @DisplayName("limit 상수(10)를 recent·followUps에 전달한다")
    void limit_상수를_협력자에_전달한다() {
        givenEmptyAggregates();

        dashboardService.summary(ctx(AccessScope.COMPANY_ALL), MONTH);

        verify(activityQuery).recent(any(), eq(10));
        verify(taskQuery).followUps(any(), eq(10));
    }

    @Test
    @DisplayName("활동·할 일이 없으면 summariesByIds를 부르지 않는다")
    void 딜이_없으면_배치_조회를_생략한다() {
        givenEmptyAggregates();

        dashboardService.summary(ctx(AccessScope.COMPANY_ALL), MONTH);

        verify(dealQuery, never()).summariesByIds(any(), any());
    }
}
