package com.twojo.deal.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.OrderQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.SalesStatsQuery;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SalesStatsQuery} 구현 — <b>전환율({@code conversions}, DB-07)만 아직 자리표시자다.</b>
 *
 * <p>D의 대시보드가 이 빈을 주입받는다. 파이프라인·이달 성사·담당자별 실적은 실값이고,
 * 전환율만 빈 목록이다 — 화면에 "0%"로 보이면 안 되는 값이라 D가 "집계 준비 중"으로
 * 구분해 표시한다 (2026-09-08 요청 협의 · 2026-09-10 재확인).
 *
 * <p><b>빈 값이 예외보다 나은지</b> — 원래 이 클래스는 {@code UnsupportedOperationException}을
 * 던졌고, 그 편이 "틀린 답이 조용히 나가는 것"보다 낫다는 것이 원칙이었다
 * ({@code QuoteQueryImpl} javadoc). 다만 그 예외가 <b>대시보드 전체를 500으로</b>
 * 만들어 D가 화면을 세울 수조차 없다. 그래서 남은 하나도 빈 값으로 두되,
 * 값이 진짜가 아니라는 사실을 계약 문서와 이 javadoc에 남긴다.
 *
 * <p><b>금액은 전부 {@code total_amount}(VAT 포함)다</b> — 파이프라인의 예상 금액과 축을 맞춘다
 * (2026-09-10 D 확정). 0건이어도 null이 아니라 0을 내보낸다 (#85 D 합의).
 *
 * <p>집계 소유가 deal 모듈인 이유 — 범위 판정 축이 {@code deal.assignee_member_id}
 * 하나뿐이고(11 §1.4), 견적·주문 집계도 전부 Deal에서 파생되기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesStatsQueryImpl implements SalesStatsQuery {


    private final DealRepository dealRepository;
    private final QuoteQuery quoteQuery;
    private final OrderQuery orderQuery;
    private final MemberQuery memberQuery;

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
                .aggregateByStage(ctx.companyId(), assigneeMemberId, Deal.PIPELINE_ORDER)
                .stream()
                .collect(Collectors.toMap(DealRepository.StageAggregate::getStage, Function.identity()));

        return Deal.PIPELINE_ORDER.stream()
                .map(stage -> {
                    DealRepository.StageAggregate row = byStage.get(stage);
                    return row == null
                            ? new StageCount(stage.name(), 0, 0L)
                            : new StageCount(stage.name(), (int) row.getCount(), row.getAmount());
                })
                .toList();
    }

    /**
     * 이달 성사 (DB-02) — <b>주문 합계</b>다 (DL-18). 예상 금액이 아니다.
     *
     * <p><b>축은 주문 전환 시각이다</b> (2026-09-10 D 확정). Deal에 성사 시각 컬럼이 없어
     * 단일 소스로 셀 수 있는 축이 {@code orders.created_at}뿐이다. 그래서 승인 견적이 여럿인 딜은
     * (Q-25) 각 달에 그 달의 주문액이 잡힌다 — "이달 <b>실현된</b> 성사 금액"으로 읽는다.
     *
     * <p><b>{@code count}는 전환 건수이지 성사 딜 수가 아니다.</b> 한 딜에서 두 건을 전환하면 2다.
     * 견적당 주문은 하나뿐이라(OD-03) 견적 수와는 같다.
     *
     * <p>영업(OWNED_ONLY)은 본인 담당 딜의 주문만 본다 (SC-02·04) — 담당 딜이 하나도 없으면
     * 빈 목록이 <b>거짓 조건</b>이 되어 0이다. 그 자리에서 "제한 없음"으로 새면 회사 전체가 잡힌다.
     */
    @Override
    public WonStats monthlyWon(AccessContext ctx, YearMonth month) {
        List<OrderQuery.QuoteWonTotal> won = orderQuery.wonTotalsByQuotes(
                ctx.companyId(), visibleQuoteIds(ctx), month.atDay(1), month.atEndOfMonth());

        long amount = won.stream().mapToLong(OrderQuery.QuoteWonTotal::totalAmount).sum();
        return new WonStats(amount, won.size());   // 0건이어도 null이 아니라 0이다 (#85 D 합의)
    }

    /**
     * 담당자별 실적 (DB-06·08) — 기업 관리자 전용이라 {@code AccessContext}가 없다.
     * 범위는 인자로 받은 {@code companyId}뿐이고, 역할 판정은 호출자(D)가 이미 했다.
     *
     * <p><b>한 메서드 안에 축이 둘이다</b> (2026-09-10 D 확정).
     * {@code wonCount}·{@code wonAmount}는 <b>기간 안</b> 전환이고,
     * {@code activeDealCount}는 <b>조회 시점 현재</b> 스냅샷이다 — {@code from}·{@code to}와 무관하다.
     * "그때 진행 중이었던"을 재구성하려면 전이 이력이 필요한데 그건 전환율(DB-07)과 같은 블로커다.
     *
     * <p><b>실적이 있는데 활성 목록에 없는 구성원도 넣는다.</b> 비활성화 시 이관되는 것은
     * <b>진행 중</b> 딜뿐이라(MB-14) 성사된 딜은 떠난 담당자에게 남는다. 활성만 추리면 그 금액이
     * 사라져 {@code monthlyWon} 합계와 어긋난다 — 화면에서 바로 눈에 띄는 종류의 불일치다.
     *
     * <p>정렬은 성사 금액 내림차순이다 — "비교해 보는" 화면이라(DB-06) 큰 쪽이 위로 온다.
     * 같으면 이름순으로 고정해 호출마다 순서가 흔들리지 않게 한다.
     */
    @Override
    public List<MemberPerformance> performance(UUID companyId, LocalDate from, LocalDate to) {
        List<OrderQuery.QuoteWonTotal> won = orderQuery.wonTotalsByQuotes(
                companyId, null, from, to);   // quoteIds null = 회사 전체 (SC-05)

        Map<UUID, UUID> dealByQuote = quoteQuery
                .originsByIds(companyId, won.stream().map(OrderQuery.QuoteWonTotal::quoteId).toList())
                .stream()
                .collect(Collectors.toMap(QuoteQuery.QuoteOrigin::quoteId, QuoteQuery.QuoteOrigin::dealId));
        Map<UUID, UUID> assigneeByDeal = dealRepository
                .findByCompanyIdAndIdInAndDeletedAtIsNull(companyId, dealByQuote.values())
                .stream()
                .collect(Collectors.toMap(Deal::getId, Deal::getAssigneeMemberId));

        Map<UUID, long[]> byMember = new LinkedHashMap<>();   // [금액, 건수]
        for (OrderQuery.QuoteWonTotal row : won) {
            UUID dealId = dealByQuote.get(row.quoteId());
            UUID memberId = dealId == null ? null : assigneeByDeal.get(dealId);
            if (memberId == null) {
                continue;   // 딜이 소프트 삭제된 주문 — 담당자를 정할 수 없다
            }
            long[] sum = byMember.computeIfAbsent(memberId, key -> new long[2]);
            sum[0] += row.totalAmount();
            sum[1]++;
        }

        Map<UUID, Long> activeByMember = dealRepository
                .countOpenByAssignee(companyId, Deal.OPEN_STAGES).stream()
                .collect(Collectors.toMap(DealRepository.AssigneeCount::getMemberId,
                        DealRepository.AssigneeCount::getCount));

        Map<UUID, String> names = new LinkedHashMap<>();
        memberQuery.findAllActive(companyId)
                .forEach(member -> names.put(member.id(), member.name()));
        byMember.keySet().stream()
                .filter(memberId -> !names.containsKey(memberId))
                .forEach(memberId -> names.put(memberId, memberQuery.get(memberId).name()));

        return names.entrySet().stream()
                .map(entry -> {
                    long[] sum = byMember.getOrDefault(entry.getKey(), new long[2]);
                    return new MemberPerformance(entry.getKey(), entry.getValue(),
                            (int) sum[1], sum[0],
                            activeByMember.getOrDefault(entry.getKey(), 0L).intValue());
                })
                .sorted(Comparator.comparingLong(MemberPerformance::wonAmount).reversed()
                        .thenComparing(MemberPerformance::name))
                .toList();
    }

    /**
     * 영업이면 담당 딜의 견적으로 좁히고, 기업 관리자면 {@code null}(제한 없음)이다.
     *
     * <p>주문에는 담당자 축이 없어 견적 id로 좁힌다 (SC-04) — {@code OrderService.list}와 같은 경로다.
     * <b>담당 딜이 0건이면 빈 목록을 그대로 넘긴다</b>. 그것이 "아무것도"이고, null로 바꾸면 "전부"가 된다.
     */
    private Collection<UUID> visibleQuoteIds(AccessContext ctx) {
        if (ctx.scope() != AccessScope.OWNED_ONLY) {
            return null;
        }
        return quoteQuery.quoteIdsByDeals(ctx.companyId(),
                dealRepository.findIdsByAssignee(ctx.companyId(), ctx.memberId()));
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
