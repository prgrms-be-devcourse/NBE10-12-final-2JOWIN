package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.boundary.SalesStatsQuery.StageCount;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
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

    @Mock private DealRepository dealRepository;
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
}
