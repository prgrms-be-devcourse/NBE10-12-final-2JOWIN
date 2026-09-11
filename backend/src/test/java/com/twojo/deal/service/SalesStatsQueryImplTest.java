package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.AuditQuery;
import com.twojo.boundary.Role;
import com.twojo.boundary.SalesStatsQuery.StageConversion;
import com.twojo.boundary.SalesStatsQuery.StageCount;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 대시보드 파이프라인 집계 (DB-01) — <b>범위 판정</b>과 <b>빈 단계 채우기</b>를 본다.
 *
 * <p>집계 산수는 DB가 하므로 여기서 다시 세지 않는다. 목으로만 드러나는 것은 둘이다:
 * 영업일 때 담당자 축이 실제로 쿼리에 걸리는가, 그리고 결과에 없는 단계가 0으로 채워지는가.
 *
 * <p><b>범위가 왜 검증 대상인가.</b> 영업에게 회사 전체 파이프라인이 보이면 SC-02가 통째로 뚫린다.
 * 쿼리에 null이 넘어가면 그 순간 "제한 없음"이 되므로, 넘기는 값 자체를 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class SalesStatsQueryImplTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Mock private DealRepository dealRepository;
    @Mock private AuditQuery auditQuery;
    @InjectMocks private SalesStatsQueryImpl salesStatsQuery;

    private static DealRepository.StageAggregate row(Deal.Stage stage, long count, Long amount) {
        return new DealRepository.StageAggregate() {
            @Override public Deal.Stage getStage() {
                return stage;
            }

            @Override public long getCount() {
                return count;
            }

            @Override public Long getAmount() {
                return amount;
            }
        };
    }

    private static AccessContext ctx(Role role, AccessScope scope) {
        return new AccessContext(COMPANY_ID, MEMBER_ID, role, scope);
    }

    @Test
    @DisplayName("영업은 담당자 축이 쿼리에 걸린다 — 회사 전체가 보이면 SC-02가 뚫린다")
    void 영업_범위() {
        given(dealRepository.aggregateByStage(eq(COMPANY_ID), eq(MEMBER_ID), any()))
                .willReturn(List.of(row(Deal.Stage.LEAD, 2, 1_000_000L)));

        List<StageCount> result = salesStatsQuery.pipeline(ctx(Role.SALES_REP, AccessScope.OWNED_ONLY));

        assertThat(result).extracting(StageCount::stage)
                .containsExactly("LEAD", "CONSULT", "QUOTE", "NEGOTIATION");
        assertThat(result.getFirst().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("기업 관리자는 담당자 제한 없이(null) 회사 전체를 본다 (SC-05)")
    void 관리자_범위() {
        given(dealRepository.aggregateByStage(eq(COMPANY_ID), isNull(), any()))
                .willReturn(List.of(row(Deal.Stage.QUOTE, 5, 7_000_000L)));

        List<StageCount> result = salesStatsQuery.pipeline(ctx(Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL));

        assertThat(result).filteredOn(s -> s.stage().equals("QUOTE"))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.count()).isEqualTo(5);
                    assertThat(s.expectedAmountSum()).isEqualTo(7_000_000L);
                });
    }

    /**
     * {@code group by}는 건수 0인 단계를 아예 돌려주지 않는다. 그대로 내보내면 화면의 칸이
     * <b>사라져</b> "리드가 0건"과 "리드 칸이 없음"이 구별되지 않는다.
     */
    @Test
    @DisplayName("결과에 없는 단계는 0으로 채워 네 칸을 항상 세운다 (DB-01)")
    void 빈_단계_채우기() {
        given(dealRepository.aggregateByStage(any(), any(), any()))
                .willReturn(List.of(row(Deal.Stage.NEGOTIATION, 1, 3_000_000L)));

        List<StageCount> result = salesStatsQuery.pipeline(ctx(Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL));

        assertThat(result).hasSize(4);
        assertThat(result).filteredOn(s -> !s.stage().equals("NEGOTIATION"))
                .allSatisfy(s -> {
                    assertThat(s.count()).isZero();
                    assertThat(s.expectedAmountSum()).isZero();
                });
    }

    /**
     * 종결(WON·LOST)은 파이프라인이 아니다 — 성사 금액은 예상 금액이 아니라 주문 합계다
     * (DL-18, 08 v1.6.1). 넘기는 단계 목록 자체를 고정한다.
     */
    @Test
    @DisplayName("진행 단계 넷만 조회한다 — 종결은 파이프라인에 없다")
    void 종결은_제외() {
        given(dealRepository.aggregateByStage(any(), any(), any())).willReturn(List.of());

        salesStatsQuery.pipeline(ctx(Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL));

        org.mockito.ArgumentCaptor<Collection<Deal.Stage>> stages =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        org.mockito.Mockito.verify(dealRepository).aggregateByStage(any(), any(), stages.capture());
        assertThat(stages.getValue())
                .containsExactly(Deal.Stage.LEAD, Deal.Stage.CONSULT, Deal.Stage.QUOTE, Deal.Stage.NEGOTIATION)
                .doesNotContain(Deal.Stage.WON, Deal.Stage.LOST);
    }

    // ----- 전환율 (DB-07, #307) — 도달 기준 -----

    private static DealRepository.StageSnapshot deal(UUID id, Deal.Stage stage, String lostFrom) {
        return new DealRepository.StageSnapshot() {
            @Override public UUID getId() {
                return id;
            }

            @Override public Deal.Stage getStage() {
                return stage;
            }

            @Override public String getLostFromStage() {
                return lostFrom;
            }
        };
    }

    private static AuditQuery.StageChange moved(UUID dealId, String before, String after) {
        return new AuditQuery.StageChange(dealId, before, after, Instant.now());
    }

    private void 모집단(DealRepository.StageSnapshot... deals) {
        given(dealRepository.findStageSnapshotsCreatedBetween(eq(COMPANY_ID), any(), any()))
                .willReturn(List.of(deals));
    }

    private static double rateOf(List<StageConversion> rows, String from) {
        return rows.stream().filter(r -> r.fromStage().equals(from)).findFirst().orElseThrow().rate();
    }

    /**
     * <b>이 테스트가 이 이슈의 핵심이다.</b> 모집단을 전이 이력에서 뽑으면 리드에 멈춘 딜은
     * {@code audit_log}에 행이 없어 분모에서 통째로 빠지고, 전환율이 늘 1에 가깝게 나온다
     * (딜 생성은 감사 이벤트가 아니다). 모집단이 {@code deal} 테이블이라 그 딜도 분모에 들어간다.
     */
    @Test
    @DisplayName("한 번도 안 움직인 리드 딜도 분모에 들어간다 — 전환율이 1로 붙지 않는다")
    void 정체된_딜이_분모에_있다() {
        모집단(deal(UUID.randomUUID(), Deal.Stage.LEAD, null),
                deal(UUID.randomUUID(), Deal.Stage.LEAD, null),
                deal(UUID.randomUUID(), Deal.Stage.LEAD, null),
                deal(UUID.randomUUID(), Deal.Stage.CONSULT, null));
        given(auditQuery.stageChanges(eq(COMPANY_ID), any(), any())).willReturn(List.of());

        List<StageConversion> rows = salesStatsQuery.conversions(COMPANY_ID, FROM, TO);

        assertThat(rateOf(rows, "LEAD")).isEqualTo(0.25d);   // 4건 중 1건만 상담 이상
    }

    /**
     * 실패(LOST)는 단계 순서 밖이라 현재 값으로는 도달 지점을 알 수 없다 — 실패 직전 단계가
     * 그 딜이 닿은 곳이다 (DL-10·12). 실패했다는 사실이 딜을 분모에서 빼지도 않는다.
     */
    @Test
    @DisplayName("실패한 딜은 실패 직전 단계까지 도달한 것으로 센다")
    void 실패는_직전_단계로_되짚는다() {
        모집단(deal(UUID.randomUUID(), Deal.Stage.LOST, "QUOTE"),
                deal(UUID.randomUUID(), Deal.Stage.LOST, "LEAD"));
        given(auditQuery.stageChanges(eq(COMPANY_ID), any(), any())).willReturn(List.of());

        List<StageConversion> rows = salesStatsQuery.conversions(COMPANY_ID, FROM, TO);

        assertThat(rateOf(rows, "LEAD")).isEqualTo(0.5d);      // 둘 중 하나만 상담 이상
        assertThat(rateOf(rows, "CONSULT")).isEqualTo(1.0d);   // 상담 도달 1건이 전부 견적까지
        assertThat(rateOf(rows, "QUOTE")).isZero();            // 협상에 닿은 딜은 없다
    }

    /**
     * 되돌리기(DL-08)를 겪은 딜은 <b>현재 단계가 최고 도달보다 낮다.</b> 그 차이는 이력에만 있고,
     * 이것이 {@code audit_log}가 이 계산에 필요한 유일한 이유다 — 나머지는 {@code deal}이 답한다.
     */
    @Test
    @DisplayName("되돌린 딜의 봉우리는 이력이 보정한다 — 현재 단계보다 멀리 갔던 것을 센다")
    void 되돌린_딜은_이력이_보정한다() {
        UUID 되돌아온딜 = UUID.randomUUID();
        모집단(deal(되돌아온딜, Deal.Stage.CONSULT, null));
        given(auditQuery.stageChanges(eq(COMPANY_ID), any(), any()))
                .willReturn(List.of(moved(되돌아온딜, "CONSULT", "QUOTE"),
                        moved(되돌아온딜, "QUOTE", "CONSULT")));

        List<StageConversion> rows = salesStatsQuery.conversions(COMPANY_ID, FROM, TO);

        assertThat(rateOf(rows, "CONSULT")).isEqualTo(1.0d);   // 현재는 상담이지만 견적까지 갔었다
    }

    /**
     * 같은 딜이 여러 번 오가도 최고 도달 단계 하나로 접힌다 — 고유 딜로 센다 (D 확정 3).
     * 연인원으로 세면 {@code rate}가 1을 넘어 계약(0~1)을 깬다.
     */
    @Test
    @DisplayName("같은 딜이 여러 번 오가도 한 건이다 — rate가 1을 넘지 않는다")
    void 왕복해도_한_건이다() {
        UUID 왕복딜 = UUID.randomUUID();
        모집단(deal(왕복딜, Deal.Stage.CONSULT, null));
        given(auditQuery.stageChanges(eq(COMPANY_ID), any(), any()))
                .willReturn(List.of(moved(왕복딜, "LEAD", "CONSULT"),
                        moved(왕복딜, "CONSULT", "LEAD"),
                        moved(왕복딜, "LEAD", "CONSULT")));

        List<StageConversion> rows = salesStatsQuery.conversions(COMPANY_ID, FROM, TO);

        assertThat(rows).allSatisfy(r -> assertThat(r.rate()).isBetween(0d, 1d));
        assertThat(rateOf(rows, "LEAD")).isEqualTo(1.0d);
    }

    /**
     * 자동 성사(OD-06)는 단계와 무관하게 WON으로 직행한다 — 리드에서 바로 성사된 딜도
     * 중간 단계를 전부 도달한 것으로 본다. 도달 기준을 택한 이유 중 하나다 (D 확정 4).
     */
    @Test
    @DisplayName("리드에서 바로 성사된 딜도 중간 단계를 도달로 센다 (OD-06)")
    void 자동_성사는_전_단계_도달이다() {
        모집단(deal(UUID.randomUUID(), Deal.Stage.WON, null));
        given(auditQuery.stageChanges(eq(COMPANY_ID), any(), any())).willReturn(List.of());

        List<StageConversion> rows = salesStatsQuery.conversions(COMPANY_ID, FROM, TO);

        assertThat(rows).hasSize(4).allSatisfy(r -> assertThat(r.rate()).isEqualTo(1.0d));
    }

    /**
     * 코호트 밖 딜의 전이가 섞여 들어와도 분모·분자를 흔들지 않는다 —
     * {@code stageChanges}는 기간 안 전이를 <b>전부</b> 주므로 기간 전에 등록된 딜의 행도 온다.
     */
    @Test
    @DisplayName("기간 밖에 등록된 딜의 전이는 무시한다")
    void 코호트_밖_전이는_버린다() {
        모집단(deal(UUID.randomUUID(), Deal.Stage.LEAD, null));
        given(auditQuery.stageChanges(eq(COMPANY_ID), any(), any()))
                .willReturn(List.of(moved(UUID.randomUUID(), "QUOTE", "NEGOTIATION")));

        List<StageConversion> rows = salesStatsQuery.conversions(COMPANY_ID, FROM, TO);

        assertThat(rows).allSatisfy(r -> assertThat(r.rate()).isZero());
    }

    /** 이력도 딜도 없는 기간은 예외가 아니다 — 초기 상태가 곧 정상 상태다 (완료 조건 4) */
    @Test
    @DisplayName("기간에 등록된 딜이 없으면 네 칸이 0으로 선다 — 예외가 아니다")
    void 모집단이_없으면_0이다() {
        모집단();

        List<StageConversion> rows = salesStatsQuery.conversions(COMPANY_ID, FROM, TO);

        assertThat(rows).hasSize(4).allSatisfy(r -> assertThat(r.rate()).isZero());
        assertThat(rows).extracting(StageConversion::fromStage)
                .containsExactly("LEAD", "CONSULT", "QUOTE", "NEGOTIATION");
        assertThat(rows).extracting(StageConversion::toStage)
                .containsExactly("CONSULT", "QUOTE", "NEGOTIATION", "WON");
    }
}
