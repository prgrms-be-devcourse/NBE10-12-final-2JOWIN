package com.twojo.dashboard.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.SalesStatsQuery;
import com.twojo.dashboard.dto.DashboardSummaryResponse;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Supplier;
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
 * <p><b>C 미구현부 우회(degrade)</b> — C의 {@link SalesStatsQuery} 4종과
 * {@link QuoteQuery#findAwaitingResponse}가 아직 {@code UnsupportedOperationException}을 던진다.
 * 그 예외만 잡아 해당 섹션을 빈 값으로 채우고 200을 유지한다 — 다른 예외는 전파한다.
 * C 실구현이 머지되면 이 방어는 죽은 코드가 되어 제거한다 (issue #202).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final SalesStatsQuery.WonStats ZERO_WON = new SalesStatsQuery.WonStats(0L, 0);

    private final SalesStatsQuery salesStatsQuery;
    private final QuoteQuery quoteQuery;

    /**
     * 요약 (DB-01~05). 스코프는 {@code ctx}로 각 협력자가 해석한다 — 단, DB-03 응답 대기는
     * {@link QuoteQuery#findAwaitingResponse}가 {@code companyId}만 받고 {@code QuoteSummary}에
     * {@code dealId}가 없어 영업 담당자 본인 필터가 불가능하다. 회사 전체 견적 노출은 데이터 누수라
     * <b>영업 담당자(OWNED_ONLY)에게는 빈 목록을 강제</b>한다 (v1 한계 — C가 {@code QuoteSummary.dealId}를
     * 추가하면 실필터로 전환, issue #202).
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

        // DB-04(최근 활동)·DB-05(후속 필요)는 다음 커밋에서 채운다.
        return new DashboardSummaryResponse(
                pipeline, won.amount(), won.count(), waitingQuotes, List.of(), List.of());
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
}
