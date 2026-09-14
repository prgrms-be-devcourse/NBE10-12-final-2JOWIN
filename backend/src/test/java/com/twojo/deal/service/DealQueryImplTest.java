package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.DealQuery.DealSummary;
import com.twojo.boundary.OrderQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 경계 계약의 매핑·예외 변환 검증 (B의 CU-08·12·SC-02, D의 Q-26·AP-18).
 *
 * <p>소프트 삭제 제외와 회사 스코프는 Repository 쿼리 이름에 박혀 있어 여기서는 검증하지 않는다 —
 * 실질 검증은 컨트롤러가 붙은 뒤 "타사 리소스를 요청하면 404가 오는가"로 한다.
 * 성사 금액이 실제 주문 행에서 합쳐지는지는 {@code DealSummaryWonAmountIntegrationTest}가 실제 DB로 본다.
 */
@ExtendWith(MockitoExtension.class)
class DealQueryImplTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();

    @Mock private DealRepository dealRepository;
    @Mock private ObjectProvider<QuoteQuery> quoteQueryProvider;
    @Mock private OrderQuery orderQuery;
    @Mock private QuoteQuery quoteQuery;
    @InjectMocks private DealQueryImpl dealQuery;

    /**
     * 생성은 팩토리로, 단계·id는 리플렉션으로 세운다.
     * 전이 메서드로 옮길 수도 있지만(#61), 여기서 보는 것은 매핑이지 전이가 아니라 직접 세운다.
     */
    private static Deal deal(Deal.Stage stage, String title, UUID assigneeId) {
        Deal deal = Deal.create(COMPANY_ID, UUID.randomUUID(), assigneeId, title, 5_000_000L, null);
        ReflectionTestUtils.setField(deal, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(deal, "stage", stage);
        return deal;
    }

    private static QuoteQuery.QuoteBrief quote(UUID quoteId, UUID dealId) {
        return new QuoteQuery.QuoteBrief(quoteId, dealId, "Q-" + quoteId.toString().substring(0, 4),
                "APPROVED", 0L, Instant.now());
    }

    private static OrderQuery.OrderBrief order(UUID quoteId, long total) {
        return new OrderQuery.OrderBrief(UUID.randomUUID(), quoteId, "O-" + quoteId.toString().substring(0, 4),
                total, Instant.now());
    }

    @Test
    @DisplayName("담당자 id를 돌려준다 — D의 알림 수신자 결정 (Q-26)")
    void assigneeIdOf_returnsAssignee() {
        UUID dealId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        given(dealRepository.findByIdAndDeletedAtIsNull(dealId))
                .willReturn(Optional.of(deal(Deal.Stage.QUOTE, "한빛 사무가구", assigneeId)));

        assertThat(dealQuery.assigneeIdOf(dealId)).isEqualTo(assigneeId);
    }

    @Test
    @DisplayName("고객사 id를 돌려준다 — 견적 발송의 수신인 검증 기준값 (QT-13)")
    void customerIdOf_returnsCustomer() {
        UUID dealId = UUID.randomUUID();
        Deal deal = deal(Deal.Stage.QUOTE, "한빛 사무가구", UUID.randomUUID());
        given(dealRepository.findByIdAndDeletedAtIsNull(dealId)).willReturn(Optional.of(deal));

        assertThat(dealQuery.customerIdOf(dealId)).isEqualTo(deal.getCustomerId());
    }

    @Test
    @DisplayName("없는 Deal의 고객사를 물으면 RESOURCE_NOT_FOUND — 검증 기준값이 없으면 발송을 진행할 수 없다")
    void customerIdOf_throwsWhenMissing() {
        UUID dealId = UUID.randomUUID();
        given(dealRepository.findByIdAndDeletedAtIsNull(dealId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> dealQuery.customerIdOf(dealId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 Deal의 담당자를 물으면 RESOURCE_NOT_FOUND — 조용히 null을 돌려주지 않는다")
    void assigneeIdOf_throwsWhenMissing() {
        UUID dealId = UUID.randomUUID();
        given(dealRepository.findByIdAndDeletedAtIsNull(dealId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> dealQuery.assigneeIdOf(dealId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ParameterizedTest(name = "{0} 단계는 진행 중이다")
    @EnumSource(value = Deal.Stage.class, names = {"LEAD", "CONSULT", "QUOTE", "NEGOTIATION"})
    @DisplayName("리드~협상은 진행 중 (전이표 §5)")
    void isOpen_trueForOpenStages(Deal.Stage stage) {
        UUID dealId = UUID.randomUUID();
        given(dealRepository.findByIdAndDeletedAtIsNull(dealId))
                .willReturn(Optional.of(deal(stage, "진행 중", UUID.randomUUID())));

        assertThat(dealQuery.isOpen(dealId)).isTrue();
    }

    @ParameterizedTest(name = "{0} 단계는 종결이다")
    @EnumSource(value = Deal.Stage.class, names = {"WON", "LOST"})
    @DisplayName("성사·실패는 종결 — 견적을 더 붙일 수 없다 (Q-25)")
    void isOpen_falseForClosedStages(Deal.Stage stage) {
        UUID dealId = UUID.randomUUID();
        given(dealRepository.findByIdAndDeletedAtIsNull(dealId))
                .willReturn(Optional.of(deal(stage, "종결", UUID.randomUUID())));

        assertThat(dealQuery.isOpen(dealId)).isFalse();
    }

    @Test
    @DisplayName("없는 Deal은 진행 중이 아니다 — \"여기 견적을 더 붙여도 되는가\"의 답은 안 된다")
    void isOpen_falseWhenMissing() {
        UUID dealId = UUID.randomUUID();
        given(dealRepository.findByIdAndDeletedAtIsNull(dealId)).willReturn(Optional.empty());

        assertThat(dealQuery.isOpen(dealId)).isFalse();
    }

    @Test
    @DisplayName("진행 중 Deal이 있으면 고객사를 삭제할 수 없다 (CU-08)")
    void hasOpenDeals_delegatesWithOpenStages() {
        UUID customerId = UUID.randomUUID();
        given(dealRepository.existsByCustomerIdAndStageInAndDeletedAtIsNull(customerId, Deal.OPEN_STAGES))
                .willReturn(true);

        assertThat(dealQuery.hasOpenDeals(customerId)).isTrue();
    }

    @Test
    @DisplayName("진행 중 담당 Deal 건수 — 종결을 빼고 센다 (MB-14, 이관이 옮기는 집합과 동일)")
    void countOpenAssigned_countsOpenStagesOnly() {
        UUID memberId = UUID.randomUUID();
        given(dealRepository.countByCompanyIdAndAssigneeMemberIdAndStageInAndDeletedAtIsNull(
                COMPANY_ID, memberId, Deal.OPEN_STAGES)).willReturn(3L);

        assertThat(dealQuery.countOpenAssigned(COMPANY_ID, memberId)).isEqualTo(3L);
    }

    @Test
    @DisplayName("고객사 Deal 이력을 요약으로 돌려준다 — 종결 포함 (CU-12)")
    void summariesByCustomer_mapsAllStages() {
        UUID customerId = UUID.randomUUID();
        given(quoteQueryProvider.getObject()).willReturn(quoteQuery);
        given(dealRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId))
                .willReturn(List.of(deal(Deal.Stage.WON, "성사 건", UUID.randomUUID()),
                        deal(Deal.Stage.QUOTE, "진행 건", UUID.randomUUID())));

        List<DealSummary> summaries = dealQuery.summariesByCustomer(customerId);

        assertThat(summaries).hasSize(2)
                .extracting(DealSummary::title, DealSummary::stage)
                .containsExactly(tuple("성사 건", "WON"), tuple("진행 건", "QUOTE"));
    }

    /**
     * <b>이 테스트가 이 수정의 핵심이다.</b> 지금까지 {@code wonAmount}는 항상 null이었고,
     * 고객사 상세의 Deal 이력은 성사 딜 금액을 "—"로 그렸다.
     *
     * <p>성사 딜 하나가 견적 두 건·주문 두 건을 가진 경우다 — 주문 합계는 견적을 지나 모인다.
     * 진행 중인 딜은 null이고, 견적 조회에는 <b>성사 딜의 id만</b> 넘어간다.
     */
    @Test
    @DisplayName("성사 딜에는 주문 합계를 싣고 진행 중 딜은 null이다 (DL-18)")
    void summariesByCustomer_fillsWonAmountFromOrders() {
        UUID customerId = UUID.randomUUID();
        Deal won = deal(Deal.Stage.WON, "성사 건", UUID.randomUUID());
        Deal open = deal(Deal.Stage.QUOTE, "진행 건", UUID.randomUUID());
        UUID quote1 = UUID.randomUUID();
        UUID quote2 = UUID.randomUUID();
        given(dealRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId))
                .willReturn(List.of(won, open));
        given(quoteQueryProvider.getObject()).willReturn(quoteQuery);
        given(quoteQuery.briefsByDeals(COMPANY_ID, List.of(won.getId())))
                .willReturn(List.of(quote(quote1, won.getId()), quote(quote2, won.getId())));
        given(orderQuery.briefsByQuotes(eq(COMPANY_ID),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(quote1, quote2)))))
                .willReturn(List.of(order(quote1, 1_320_000L), order(quote2, 880_000L)));

        assertThat(dealQuery.summariesByCustomer(customerId))
                .extracting(DealSummary::title, DealSummary::expectedAmount, DealSummary::wonAmount)
                .containsExactly(tuple("성사 건", 5_000_000L, 2_200_000L), tuple("진행 건", 5_000_000L, null));
    }

    @Test
    @DisplayName("성사 딜이 없으면 견적·주문을 조회하지 않는다 — 제목만 필요한 호출은 비용이 0이다")
    void summariesByCustomer_skipsOrderLookupWithoutWonDeal() {
        UUID customerId = UUID.randomUUID();
        given(dealRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId))
                .willReturn(List.of(deal(Deal.Stage.NEGOTIATION, "협상 건", UUID.randomUUID())));

        assertThat(dealQuery.summariesByCustomer(customerId))
                .singleElement()
                .extracting(DealSummary::wonAmount)
                .isNull();

        then(quoteQueryProvider).should(never()).getObject();
        then(orderQuery).should(never()).briefsByQuotes(any(), any());
    }

    @Test
    @DisplayName("딜이 없는 고객사는 빈 목록이고 아무것도 더 조회하지 않는다")
    void summariesByCustomer_emptyWhenNoDeals() {
        UUID customerId = UUID.randomUUID();
        given(dealRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId))
                .willReturn(List.of());

        assertThat(dealQuery.summariesByCustomer(customerId)).isEmpty();

        then(quoteQueryProvider).should(never()).getObject();
    }

    @Test
    @DisplayName("빈 id 목록은 조회하지 않고 빈 목록을 돌려준다")
    void summariesByIds_skipsQueryOnEmptyInput() {
        assertThat(dealQuery.summariesByIds(COMPANY_ID, List.of())).isEmpty();

        then(dealRepository).should(never()).findByCompanyIdAndIdInAndDeletedAtIsNull(any(), any());
    }

    @Test
    @DisplayName("id 묶음으로 요약을 배치 조회한다 — 줄마다 호출하지 않는다 (DB-04·05)")
    void summariesByIds_batchesLookup() {
        List<UUID> dealIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        given(dealRepository.findByCompanyIdAndIdInAndDeletedAtIsNull(COMPANY_ID, dealIds))
                .willReturn(List.of(deal(Deal.Stage.CONSULT, "상담 건", UUID.randomUUID())));

        assertThat(dealQuery.summariesByIds(COMPANY_ID, dealIds))
                .singleElement()
                .extracting(DealSummary::title)
                .isEqualTo("상담 건");
    }

    /** 계약 javadoc이 두 창구 모두 성사 금액을 싣는다고 적는다 — 같은 헬퍼를 지나는지 본다 */
    @Test
    @DisplayName("id 묶음 조회도 성사 딜에는 주문 합계를 싣는다 (DL-18)")
    void summariesByIds_fillsWonAmountToo() {
        Deal won = deal(Deal.Stage.WON, "성사 건", UUID.randomUUID());
        UUID quoteId = UUID.randomUUID();
        List<UUID> dealIds = List.of(won.getId());
        given(dealRepository.findByCompanyIdAndIdInAndDeletedAtIsNull(COMPANY_ID, dealIds))
                .willReturn(List.of(won));
        given(quoteQueryProvider.getObject()).willReturn(quoteQuery);
        given(quoteQuery.briefsByDeals(COMPANY_ID, List.of(won.getId())))
                .willReturn(List.of(quote(quoteId, won.getId())));
        given(orderQuery.briefsByQuotes(eq(COMPANY_ID), argThat(ids -> ids.size() == 1 && ids.contains(quoteId))))
                .willReturn(List.of(order(quoteId, 8_800_000L)));

        assertThat(dealQuery.summariesByIds(COMPANY_ID, dealIds))
                .singleElement()
                .extracting(DealSummary::wonAmount)
                .isEqualTo(8_800_000L);
    }

    @Test
    @DisplayName("담당 Deal id 목록을 회사 스코프와 함께 조회한다 (SC-02)")
    void assignedDealIds_delegatesWithCompanyScope() {
        UUID memberId = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID());
        given(dealRepository.findIdsByAssignee(COMPANY_ID, memberId)).willReturn(ids);

        assertThat(dealQuery.assignedDealIds(COMPANY_ID, memberId)).isEqualTo(ids);
    }
}
