package com.twojo.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.twojo.dashboard.dto.DashboardPerformanceResponse;
import com.twojo.dashboard.dto.DashboardSummaryResponse;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
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
 * {@link DashboardService} — 집계 섹션 매핑, 영업 담당자의 응답 대기 본인 담당 필터(SC-02),
 * DB-04·05의 딜 제목 조립과 소프트 삭제 행 제외, performance 역할 가드·기간 검증을 고정한다.
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
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);

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

    private static QuoteQuery.QuoteSummary awaiting(String quoteNo, UUID dealId) {
        return new QuoteQuery.QuoteSummary(UUID.randomUUID(), quoteNo, dealId, COMPANY_ID, null,
                Instant.parse("2026-09-01T00:00:00Z"), null, LocalDate.of(2026, 9, 30));
    }

    private static ActivityQuery.RecentActivitySummary act(UUID dealId, String summary) {
        return new ActivityQuery.RecentActivitySummary(dealId, summary, Instant.parse("2026-09-05T00:00:00Z"));
    }

    private static TaskQuery.FollowUpSummary task(UUID taskId, UUID dealId, String content) {
        return new TaskQuery.FollowUpSummary(taskId, dealId, content, LocalDate.of(2026, 9, 10));
    }

    private static DealQuery.DealSummary deal(UUID id, String title) {
        return new DealQuery.DealSummary(id, UUID.randomUUID(), title, "QUOTE", 1_000_000L, null,
                Instant.parse("2026-08-01T00:00:00Z"));
    }

    private static SalesStatsQuery.MemberPerformance perf(String name, long wonAmount) {
        return new SalesStatsQuery.MemberPerformance(UUID.randomUUID(), name, 2, wonAmount, 3);
    }

    private static SalesStatsQuery.StageConversion conv(String from, String to, double rate) {
        return new SalesStatsQuery.StageConversion(from, to, rate);
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
                .willReturn(List.of(awaiting("Q-2609-001", DEAL_A)));

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
    @DisplayName("관리자는 회사 전체 응답 대기를 받고 담당 딜 조회를 하지 않는다")
    void 관리자는_회사_전체_응답대기를_받는다() {
        given(salesStatsQuery.pipeline(any())).willReturn(List.of());
        given(salesStatsQuery.monthlyWon(any(), any())).willReturn(new SalesStatsQuery.WonStats(0L, 0));
        given(quoteQuery.findAwaitingResponse(COMPANY_ID))
                .willReturn(List.of(awaiting("Q-A", DEAL_A), awaiting("Q-B", DEAL_B)));

        DashboardSummaryResponse res = dashboardService.summary(ctx(AccessScope.COMPANY_ALL), MONTH);

        assertThat(res.waitingQuotes())
                .extracting(DashboardSummaryResponse.WaitingQuote::quoteNo)
                .containsExactly("Q-A", "Q-B");
        verify(dealQuery, never()).assignedDealIds(any(), any());
    }

    @Test
    @DisplayName("영업 담당자의 응답 대기는 본인 담당 딜의 견적만 남는다 (SC-02)")
    void 영업담당자는_본인_담당_딜의_응답대기만_받는다() {
        given(salesStatsQuery.pipeline(any())).willReturn(List.of());
        given(salesStatsQuery.monthlyWon(any(), any())).willReturn(new SalesStatsQuery.WonStats(0L, 0));
        given(quoteQuery.findAwaitingResponse(COMPANY_ID))
                .willReturn(List.of(awaiting("Q-MINE", DEAL_A), awaiting("Q-OTHER", DEAL_B)));
        given(dealQuery.assignedDealIds(COMPANY_ID, MEMBER_ID)).willReturn(List.of(DEAL_A));

        DashboardSummaryResponse res = dashboardService.summary(ctx(AccessScope.OWNED_ONLY), MONTH);

        assertThat(res.waitingQuotes())
                .extracting(DashboardSummaryResponse.WaitingQuote::quoteNo)
                .containsExactly("Q-MINE");
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

    @Test
    @DisplayName("performance는 관리자에게 members·conversions를 계약 결과로 매핑한다")
    void performance는_관리자에게_두_섹션을_매핑한다() {
        given(salesStatsQuery.performance(eq(COMPANY_ID), any(), any()))
                .willReturn(List.of(perf("박지훈", 4_000_000L)));
        given(salesStatsQuery.conversions(eq(COMPANY_ID), any(), any()))
                .willReturn(List.of(conv("QUOTE", "NEGOTIATION", 0.5)));

        DashboardPerformanceResponse res =
                dashboardService.performance(ctx(AccessScope.COMPANY_ALL), FROM, TO);

        assertThat(res.members())
                .extracting(DashboardPerformanceResponse.MemberPerformance::name)
                .containsExactly("박지훈");
        assertThat(res.conversions())
                .singleElement()
                .satisfies(c -> assertThat(c.rate()).isEqualTo(0.5));
    }

    @Test
    @DisplayName("영업 담당자가 performance를 부르면 403 FORBIDDEN이다")
    void 영업담당자의_performance는_FORBIDDEN이다() {
        assertThatThrownBy(() -> dashboardService.performance(ctx(AccessScope.OWNED_ONLY), FROM, TO))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("from이 to보다 뒤면 400 VALIDATION_FAILED다")
    void 기간_역전이면_VALIDATION_FAILED다() {
        assertThatThrownBy(() -> dashboardService.performance(ctx(AccessScope.COMPANY_ALL), TO, FROM))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("기간이 366일을 넘으면 400 VALIDATION_FAILED다")
    void 기간이_상한을_넘으면_VALIDATION_FAILED다() {
        assertThatThrownBy(() -> dashboardService.performance(
                ctx(AccessScope.COMPANY_ALL), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 3)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("performance 자리표시자(빈 목록)는 그대로 나간다 - 화면이 집계 준비 중으로 표시")
    void performance_자리표시자_빈_목록은_그대로_나간다() {
        given(salesStatsQuery.performance(eq(COMPANY_ID), any(), any())).willReturn(List.of());
        given(salesStatsQuery.conversions(eq(COMPANY_ID), any(), any())).willReturn(List.of());

        DashboardPerformanceResponse res =
                dashboardService.performance(ctx(AccessScope.COMPANY_ALL), FROM, TO);

        assertThat(res.members()).isEmpty();
        assertThat(res.conversions()).isEmpty();
    }
}
