package com.twojo.deal.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.SalesStatsQuery;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SalesStatsQuery} 구현 — <b>지금은 파이프라인(DB-01)만 실구현이다.</b>
 *
 * <p>D의 대시보드가 이 빈을 주입받는다. 나머지 셋은 아래 각 메서드의 javadoc대로
 * <b>빈 값을 돌려주는 자리표시자</b>이고, 화면에 "0"으로 보이면 안 되는 값이다 —
 * D가 "집계 준비 중"으로 구분해 표시해야 한다 (2026-09-08 요청 협의).
 *
 * <p><b>빈 값이 예외보다 나은지</b> — 원래 이 클래스는 {@code UnsupportedOperationException}을
 * 던졌고, 그 편이 "틀린 답이 조용히 나가는 것"보다 낫다는 것이 원칙이었다
 * ({@code QuoteQueryImpl} javadoc). 다만 지금은 그 예외가 <b>대시보드 전체를 500으로</b>
 * 만들어 D가 화면을 세울 수조차 없다. 그래서 <b>일시적으로</b> 빈 값으로 바꾸되,
 * 값이 진짜가 아니라는 사실을 계약 문서와 이 javadoc에 남긴다.
 *
 * <p>집계 소유가 deal 모듈인 이유 — 범위 판정 축이 {@code deal.assignee_member_id}
 * 하나뿐이고(11 §1.4), 견적·주문 집계도 전부 Deal에서 파생되기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesStatsQueryImpl implements SalesStatsQuery {

    /** 화면에 항상 네 칸이 서야 한다 — 건수 0인 단계도 0으로 보여야 빈칸과 구별된다 (DB-01) */
    private static final List<Deal.Stage> PIPELINE_STAGES =
            List.of(Deal.Stage.LEAD, Deal.Stage.CONSULT, Deal.Stage.QUOTE, Deal.Stage.NEGOTIATION);

    private final DealRepository dealRepository;

    /**
     * 진행 단계별 건수·예상 금액 (DB-01) — 종결(WON·LOST)은 제외한다.
     *
     * <p><b>WON 금액은 여기서 세지 않는다</b> — 성사 금액은 예상 금액이 아니라 주문 합계이고
     * ({@code monthlyWon}, DL-18), 08 v1.6.1이 그 정합을 명시해 두었다.
     *
     * <p>영업(OWNED_ONLY)은 본인 담당만 본다 (SC-02). 기업 관리자는 회사 전체다 (SC-05) —
     * 판정은 {@code ctx.scope()}만 읽는다. Role→Scope 변환은 인증 필터가 이미 했다.
     *
     * <p>건수 0인 단계는 쿼리 결과에 없으므로 여기서 채운다 — 그래야 화면의 네 칸이 고정된다.
     */
    @Override
    public List<StageCount> pipeline(AccessContext ctx) {
        UUID assigneeMemberId = ctx.scope() == AccessScope.OWNED_ONLY ? ctx.memberId() : null;

        Map<Deal.Stage, DealRepository.StageAggregate> byStage = dealRepository
                .aggregateByStage(ctx.companyId(), assigneeMemberId, PIPELINE_STAGES)
                .stream()
                .collect(Collectors.toMap(DealRepository.StageAggregate::getStage, Function.identity()));

        return PIPELINE_STAGES.stream()
                .map(stage -> {
                    DealRepository.StageAggregate row = byStage.get(stage);
                    return row == null
                            ? new StageCount(stage.name(), 0, 0L)
                            : new StageCount(stage.name(), (int) row.getCount(), row.getAmount());
                })
                .toList();
    }

    /**
     * <b>자리표시자 — 빈 값이다.</b> 이달 성사 금액은 <b>주문 합계</b>라(DL-18) {@code orders}를 읽어야 하는데,
     * 이 클래스는 deal 모듈이고 order는 다른 모듈이다 (11 §7.3). 경계 창구를 새로 여는 일이라
     * 별도 이슈로 다룬다 — 그때까지 D는 이 카드를 "집계 준비 중"으로 표시한다.
     */
    @Override
    public WonStats monthlyWon(AccessContext ctx, YearMonth month) {
        return new WonStats(0L, 0);
    }

    /**
     * <b>자리표시자 — 빈 목록이다.</b> {@code wonAmount}가 주문 합계라 {@code monthlyWon}과 같은 이유로
     * 막혀 있다. 건수·활성 딜 수만 먼저 내보내면 금액 칸이 0으로 보여 <b>실적을 0으로 오해</b>하게 되므로,
     * 반쪽으로 내보내지 않는다.
     */
    @Override
    public List<MemberPerformance> performance(UUID companyId, LocalDate from, LocalDate to) {
        return List.of();
    }

    /**
     * <b>자리표시자 — 빈 목록이다.</b> 단계별 전환율(DB-07)은 "언제 어느 단계에서 어디로 갔는지"가 필요한데
     * <b>전이 이력 테이블이 없다</b> — {@code deal.stage}는 현재 값 하나뿐이다. 이력을 남길지부터 정해야 하는
     * 설계 결정이라 별도 이슈로 다룬다.
     */
    @Override
    public List<StageConversion> conversions(UUID companyId, LocalDate from, LocalDate to) {
        return List.of();
    }
}
