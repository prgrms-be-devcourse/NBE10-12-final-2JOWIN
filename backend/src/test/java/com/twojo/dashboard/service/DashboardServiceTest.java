package com.twojo.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.Role;
import com.twojo.boundary.SalesStatsQuery;
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
 * {@link DashboardService#summary} — 세 집계 섹션(DB-01·02·03) 매핑, C 미구현 계약의 degrade,
 * 영업 담당자의 응답 대기 강제 빈 목록을 고정한다. DB-04·05는 다음 커밋에서 다룬다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DashboardServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("1e000000-0000-4000-8000-000000000002");
    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    @Mock
    private SalesStatsQuery salesStatsQuery;
    @Mock
    private QuoteQuery quoteQuery;
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
        assertThat(res.recentActivities()).isEmpty();   // DB-04 — 다음 커밋
        assertThat(res.followUps()).isEmpty();           // DB-05 — 다음 커밋
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
}
