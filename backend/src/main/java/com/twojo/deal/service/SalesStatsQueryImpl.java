package com.twojo.deal.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.AuditQuery;
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
 * {@link SalesStatsQuery} 구현 — <b>넷 다 실값이다</b> (전환율은 #307에서 마지막으로 채웠다).
 *
 * <p>D의 대시보드가 이 빈을 주입받는다.
 *
 * <p><b>전환율은 코드가 끝나도 화면이 바로 열리지는 않는다</b> — 되돌린 딜(DL-08)의 봉우리는
 * {@code audit_log}에만 있고 리스너(#303)가 붙기 전 전이는 남지 않았다. 모집단과 도달의 바닥은
 * {@code deal} 테이블에서 나오므로 수치가 무너지지는 않지만, 언제 "집계 준비 중"을 걷을지는
 * D가 판단한다 (2026-09-10 D 정리).
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


    /**
     * 도달 판정의 단계 순서 (DB-07) — 실패(LOST)는 <b>순서 밖</b>이라 여기 없다.
     *
     * <p>{@link Deal#PIPELINE_ORDER}를 쓰지 않는 이유는 그쪽이 <b>진행 중</b> 넷이라 성사(WON)가
     * 빠지기 때문이다. 전환율의 마지막 쌍이 {@code NEGOTIATION→WON}이라 성사가 순서 안에 있어야 한다.
     */
    private static final List<Deal.Stage> REACH_ORDER = List.of(
            Deal.Stage.LEAD, Deal.Stage.CONSULT, Deal.Stage.QUOTE, Deal.Stage.NEGOTIATION, Deal.Stage.WON);

    private static final Map<String, Deal.Stage> REACH_BY_NAME = REACH_ORDER.stream()
            .collect(Collectors.toMap(Deal.Stage::name, Function.identity()));

    /** 내보내는 쌍 — <b>인접 4쌍 고정</b>이다 (2026-09-11 D 확정). 화면의 네 칸과 1:1이다 */
    private static final List<ConversionPair> CONVERSION_PAIRS = List.of(
            new ConversionPair(Deal.Stage.LEAD, Deal.Stage.CONSULT),
            new ConversionPair(Deal.Stage.CONSULT, Deal.Stage.QUOTE),
            new ConversionPair(Deal.Stage.QUOTE, Deal.Stage.NEGOTIATION),
            new ConversionPair(Deal.Stage.NEGOTIATION, Deal.Stage.WON));

    private final DealRepository dealRepository;
    private final QuoteQuery quoteQuery;
    private final OrderQuery orderQuery;
    private final MemberQuery memberQuery;
    private final AuditQuery auditQuery;

    private record ConversionPair(Deal.Stage from, Deal.Stage to) {
    }

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
     * 단계별 전환율 (DB-07) — <b>도달 기준</b>이다 (2026-09-11 D 확정, #307).
     *
     * <pre>
     * 단계 순서: LEAD &lt; CONSULT &lt; QUOTE &lt; NEGOTIATION &lt; WON   (LOST는 순서 밖)
     * 도달(S)   = 그 딜이 S 이상 단계에 도달한 적 있음
     * rate(X→Y) = |도달(Y) 고유 딜| / |도달(X) 고유 딜|      (도달(X)=0이면 0)
     * </pre>
     *
     * <p><b>진입 이벤트가 아니라 도달로 세는 이유</b> — 되돌리기(DL-08)와 같은 딜의 왕복이
     * 모델 안에서 저절로 접힌다. 도달은 내려가지 않으므로 {@code QUOTE→CONSULT} 뒤에도
     * "QUOTE 도달"은 남고, {@code LEAD→CONSULT→LEAD→CONSULT}도 CONSULT 도달 <b>1딜</b>이다.
     * 자동 승급(Q-25)·자동 성사(OD-06)처럼 단계를 건너뛰는 전이도 최고 도달에 자연히 반영돼
     * 쌍을 인접 4개로 닫을 수 있다.
     *
     * <p><b>모집단은 기간 안에 등록된 딜이다</b> — 전이 이력이 아니다. {@code audit_log}에는
     * 움직인 딜만 남아 리드에 멈춘 딜이 분모에서 통째로 빠지고, 그러면 전환율이 늘 1 근처로 나온다
     * ({@code findStageSnapshotsCreatedBetween} javadoc). 기간은 "이때 들어온 딜이 어디까지 갔나"로
     * 읽는다 — 코호트다.
     *
     * <p><b>도달 지점은 세 원천의 최댓값이다.</b> 현재 단계가 바닥이고, 실패 딜은 실패 직전 단계로
     * 되짚으며, 되돌린 딜의 봉우리만 이력이 보탠다. 이력이 <b>보정</b>이지 원천이 아니라서
     * 리스너가 붙기 전 기간을 물어도 수치가 무너지지 않는다 — 되돌린 딜의 봉우리만 놓친다.
     *
     * <p>소수 셋째 자리에서 반올림한다 — 화면이 % 소수 첫째 자리까지 쓰기에 충분하고,
     * 프론트 목({@code mocks/handlers/dashboard.ts})과 같은 값이 나와 목↔실 API 전환에서
     * 수치가 튀지 않는다.
     *
     * <p>범위는 {@code companyId}뿐이다 — {@link #performance}와 같이 기업 관리자 전용이라
     * {@code AccessContext}를 받지 않는다 (역할 판정은 호출자가 이미 했다).
     */
    @Override
    public List<StageConversion> conversions(UUID companyId, LocalDate from, LocalDate to) {
        Map<UUID, Integer> peakByDeal = new LinkedHashMap<>();
        for (DealRepository.StageSnapshot snapshot : dealRepository.findStageSnapshotsCreatedBetween(
                companyId, DealPeriod.startOfDay(from), DealPeriod.startOfNextDay(to))) {
            peakByDeal.put(snapshot.getId(), rankOf(currentReached(snapshot)));
        }
        if (peakByDeal.isEmpty()) {
            return zeroRates();   // 이력이 없는 기간은 예외가 아니라 0이다 — 초기 상태가 곧 정상이다
        }

        // 되돌린 딜의 봉우리만 보탠다 — 코호트 밖 딜의 전이는 버린다
        for (AuditQuery.StageChange change : auditQuery.stageChanges(companyId, from, to)) {
            peakByDeal.computeIfPresent(change.dealId(),
                    (dealId, peak) -> Math.max(peak, rankOf(reachedBy(change))));
        }

        int[] reachedAtLeast = new int[REACH_ORDER.size()];
        for (int peak : peakByDeal.values()) {
            for (int rank = 0; rank <= peak; rank++) {
                reachedAtLeast[rank]++;
            }
        }

        return CONVERSION_PAIRS.stream()
                .map(pair -> {
                    int denominator = reachedAtLeast[rankOf(pair.from())];
                    double rate = denominator == 0
                            ? 0d
                            : Math.round(reachedAtLeast[rankOf(pair.to())] * 1000d / denominator) / 1000d;
                    return new StageConversion(pair.from().name(), pair.to().name(), rate);
                })
                .toList();
    }

    /**
     * 현재 값이 말하는 도달 지점 — <b>보정의 바닥</b>이다.
     *
     * <p>실패(LOST)는 단계 순서 밖이라 실패 직전 단계로 되짚는다 (DL-10·12).
     *
     * <p><b>읽히지 않는 {@code lostFromStage}는 리드로 본다</b> — 비었거나 순서 밖 값일 때다.
     * {@link Deal#lose}가 진행 중 단계만 넣으므로 정상 흐름에는 없지만, 그때 {@code valueOf}로
     * 던지면 대시보드 전체가 500이 되고 순서 밖 값을 그대로 흘리면 그 딜이 <b>모집단에서
     * 조용히 빠진다</b> — 분모가 줄면 전환율이 올라가는 방향이라 더 나쁘다. 바닥으로 흘려
     * 딜을 분모에 남긴다 ({@link #reachedBy}와 같은 처리다).
     */
    private static Deal.Stage currentReached(DealRepository.StageSnapshot snapshot) {
        if (snapshot.getStage() != Deal.Stage.LOST) {
            return snapshot.getStage();
        }
        return REACH_BY_NAME.getOrDefault(snapshot.getLostFromStage(), Deal.Stage.LEAD);
    }

    /**
     * 전이 한 줄이 말하는 도달 지점.
     *
     * <p>{@code *→LOST}는 도착이 순서 밖이라 <b>출발</b>이 도달 지점이다 — 실패했다는 사실이
     * 그 딜을 분모에서 빼지는 않는다. 그 밖의 값(표에 없는 단계)은 바닥으로 흘려 무시한다.
     */
    private static Deal.Stage reachedBy(AuditQuery.StageChange change) {
        String reached = Deal.Stage.LOST.name().equals(change.afterStage())
                ? change.beforeStage()
                : change.afterStage();
        return REACH_BY_NAME.getOrDefault(reached, Deal.Stage.LEAD);
    }

    /** 순서 안 단계의 위치 — 클수록 멀리 간 것이다 */
    private static int rankOf(Deal.Stage stage) {
        return REACH_ORDER.indexOf(stage);
    }

    /** 모집단이 없을 때의 응답 — 화면의 네 칸은 그대로 서 있어야 한다 */
    private static List<StageConversion> zeroRates() {
        return CONVERSION_PAIRS.stream()
                .map(pair -> new StageConversion(pair.from().name(), pair.to().name(), 0d))
                .toList();
    }
}
