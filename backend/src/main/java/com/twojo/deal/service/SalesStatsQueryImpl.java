package com.twojo.deal.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.SalesStatsQuery;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link SalesStatsQuery} 스텁 — 빈 자리만 채운다.
 *
 * <p>D의 대시보드(DB-01~08)가 이 빈을 주입받는다. 실제 구현은 3주차 집계 이슈에서
 * 이 클래스의 {@code throw}를 대체한다.
 *
 * <p>집계 소유가 deal 모듈인 이유 — 범위 판정 축이 {@code deal.assignee_member_id}
 * 하나뿐이고(11 §1.4), 견적·주문 집계도 전부 Deal에서 파생되기 때문이다.
 *
 * <p>{@code MemberPerformance.name}은 C가 채운다 — record 시그니처에 이미 들어 있어
 * D가 memberId마다 {@code MemberQuery.get()}을 부르지 않아도 된다 (C·D 합의, 2026-08-31).
 */
@Service
public class SalesStatsQueryImpl implements SalesStatsQuery {

    @Override
    public List<StageCount> pipeline(AccessContext ctx) {
        throw new UnsupportedOperationException("SalesStatsQuery.pipeline — C 3주차 구현 예정");
    }

    @Override
    public WonStats monthlyWon(AccessContext ctx, YearMonth month) {
        throw new UnsupportedOperationException("SalesStatsQuery.monthlyWon — C 3주차 구현 예정");
    }

    @Override
    public List<MemberPerformance> performance(UUID companyId, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("SalesStatsQuery.performance — C 3주차 구현 예정");
    }

    @Override
    public List<StageConversion> conversions(UUID companyId, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("SalesStatsQuery.conversions — C 3주차 구현 예정");
    }
}
