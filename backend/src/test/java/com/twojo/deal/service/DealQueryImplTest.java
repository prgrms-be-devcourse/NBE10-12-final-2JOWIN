package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.DealQuery.DealSummary;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 경계 계약의 매핑·예외 변환 검증 (B의 CU-08·12·SC-02, D의 Q-26·AP-18).
 *
 * <p>소프트 삭제 제외와 회사 스코프는 Repository 쿼리 이름에 박혀 있어 여기서는 검증하지 않는다 —
 * 실질 검증은 컨트롤러가 붙은 뒤 "타사 리소스를 요청하면 404가 오는가"로 한다.
 */
@ExtendWith(MockitoExtension.class)
class DealQueryImplTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();

    @Mock private DealRepository dealRepository;
    @InjectMocks private DealQueryImpl dealQuery;

    /**
     * 생성은 팩토리로, 단계·id는 리플렉션으로 세운다.
     * 단계 전이 메서드는 다음 이슈라 여기서 LEAD 밖으로 옮길 수단이 아직 없다.
     */
    private static Deal deal(Deal.Stage stage, String title, UUID assigneeId) {
        Deal deal = Deal.create(COMPANY_ID, UUID.randomUUID(), assigneeId, title, 5_000_000L, null);
        ReflectionTestUtils.setField(deal, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(deal, "stage", stage);
        return deal;
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
    @DisplayName("고객사 Deal 이력을 요약으로 돌려준다 — 종결 포함 (CU-12)")
    void summariesByCustomer_mapsAllStages() {
        UUID customerId = UUID.randomUUID();
        given(dealRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId))
                .willReturn(List.of(deal(Deal.Stage.WON, "성사 건", UUID.randomUUID()),
                        deal(Deal.Stage.QUOTE, "진행 건", UUID.randomUUID())));

        List<DealSummary> summaries = dealQuery.summariesByCustomer(customerId);

        assertThat(summaries).hasSize(2)
                .extracting(DealSummary::title, DealSummary::stage)
                .containsExactly(org.assertj.core.api.Assertions.tuple("성사 건", "WON"),
                        org.assertj.core.api.Assertions.tuple("진행 건", "QUOTE"));
    }

    @Test
    @DisplayName("wonAmount는 주문 합계라 아직 null이다 — 주문 전환 이슈에서 채운다 (DL-18)")
    void summaries_wonAmountIsNullForNow() {
        UUID customerId = UUID.randomUUID();
        given(dealRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId))
                .willReturn(List.of(deal(Deal.Stage.WON, "성사 건", UUID.randomUUID())));

        assertThat(dealQuery.summariesByCustomer(customerId))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.expectedAmount()).isEqualTo(5_000_000L);
                    assertThat(summary.wonAmount()).isNull();
                });
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

    @Test
    @DisplayName("담당 Deal id 목록을 회사 스코프와 함께 조회한다 (SC-02)")
    void assignedDealIds_delegatesWithCompanyScope() {
        UUID memberId = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID());
        given(dealRepository.findIdsByAssignee(COMPANY_ID, memberId)).willReturn(ids);

        assertThat(dealQuery.assignedDealIds(COMPANY_ID, memberId)).isEqualTo(ids);
    }
}
