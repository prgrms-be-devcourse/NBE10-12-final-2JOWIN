package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.twojo.boundary.AuditActorType;
import com.twojo.deal.DealStageChanged;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link com.twojo.boundary.DealCommand} 구현 — 조회 실패의 예외 변환을 고정한다.
 *
 * <p>단계별 승급 규칙 자체는 엔티티의 몫이라 {@code DealStageTransitionTest}가 본다.
 * 여기서 보는 것은 <b>구현체가 더하는 것</b> 하나다: 없는 Deal을 어떻게 다루는가.
 * {@code isOpen}이 같은 상황에서 {@code false}를 돌려주는 것과 달리, 여기서는 던져야 한다 —
 * "승급할 대상이 없다"를 조용히 성공으로 처리하면 발송은 끝났는데 딜은 그대로인 상태가 남는다.
 *
 * <p>이관({@code reassignOpenDeals})은 반대로 0건이 정상이다 — 세 가지를 고정한다:
 * 조회 조건이 {@code countOpenAssigned}와 같은 집합(진행 중·회사 스코프)인가 · 엔티티 메서드로 담당자를 바꾸는가 ·
 * 옮긴 id를 그대로 돌려주는가.
 */
@ExtendWith(MockitoExtension.class)
class DealCommandImplTest {

    private static final UUID DEAL_ID = UUID.randomUUID();

    @Mock private DealRepository dealRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private DealCommandImpl dealCommand;

    private static Deal dealAt(Deal.Stage stage) {
        return dealOf(UUID.randomUUID(), UUID.randomUUID(), stage);
    }

    private static Deal dealOf(UUID companyId, UUID assigneeId, Deal.Stage stage) {
        Deal deal = Deal.create(companyId, UUID.randomUUID(), assigneeId, "한빛 사무가구 30석", 5_000_000L, null);
        ReflectionTestUtils.setField(deal, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(deal, "stage", stage);
        return deal;
    }

    @Test
    @DisplayName("리드 Deal을 견적으로 올린다 — 엔티티 메서드에 그대로 위임한다")
    void 승급() {
        Deal deal = dealAt(Deal.Stage.LEAD);
        given(dealRepository.findByIdAndDeletedAtIsNull(DEAL_ID)).willReturn(Optional.of(deal));

        dealCommand.promoteToQuoteStage(DEAL_ID);

        assertThat(deal.getStage()).isEqualTo(Deal.Stage.QUOTE);
    }

    @Test
    @DisplayName("없거나 삭제된 Deal이면 RESOURCE_NOT_FOUND — 조용히 무동작하지 않는다")
    void 없는_Deal() {
        given(dealRepository.findByIdAndDeletedAtIsNull(DEAL_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> dealCommand.promoteToQuoteStage(DEAL_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("종결 Deal의 예외가 그대로 전파된다 — 구현체가 삼키지 않는다")
    void 종결_Deal은_전파() {
        given(dealRepository.findByIdAndDeletedAtIsNull(DEAL_ID))
                .willReturn(Optional.of(dealAt(Deal.Stage.WON)));

        assertThatThrownBy(() -> dealCommand.promoteToQuoteStage(DEAL_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEAL_ALREADY_WON);
    }

    @Test
    @DisplayName("진행 중 담당 Deal의 담당자를 전부 바꾸고 옮긴 id를 돌려준다 — 엔티티 경유 (MB-14)")
    void 이관() {
        UUID companyId = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Deal lead = dealOf(companyId, from, Deal.Stage.LEAD);
        Deal negotiation = dealOf(companyId, from, Deal.Stage.NEGOTIATION);
        given(dealRepository.findByCompanyIdAndAssigneeMemberIdAndStageInAndDeletedAtIsNull(
                companyId, from, Deal.OPEN_STAGES)).willReturn(List.of(lead, negotiation));

        List<UUID> moved = dealCommand.reassignOpenDeals(companyId, from, to);

        assertThat(moved).containsExactlyInAnyOrder(lead.getId(), negotiation.getId());
        assertThat(lead.getAssigneeMemberId()).isEqualTo(to);
        assertThat(negotiation.getAssigneeMemberId()).isEqualTo(to);
        assertThat(lead.getStage()).isEqualTo(Deal.Stage.LEAD);   // 단계는 건드리지 않는다
    }

    @Test
    @DisplayName("옮길 Deal이 0건이면 빈 목록 — 예외가 아니다 (종결 Deal만 남은 구성원)")
    void 이관_0건() {
        UUID companyId = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        given(dealRepository.findByCompanyIdAndAssigneeMemberIdAndStageInAndDeletedAtIsNull(
                companyId, from, Deal.OPEN_STAGES)).willReturn(List.of());

        assertThat(dealCommand.reassignOpenDeals(companyId, from, UUID.randomUUID())).isEmpty();
    }

    /**
     * 시스템 전이도 타임라인에 남는다 (AC-07, #22). 행위자는 {@code SYSTEM}이다 —
     * 딜을 움직인 것은 <b>견적 발송이라는 사건</b>이지 사람이 아니다.
     */
    @Test
    @DisplayName("승급이 실제로 일어나면 DealStageChanged(SYSTEM)를 발행한다")
    void 승급_발행() {
        Deal deal = dealAt(Deal.Stage.LEAD);
        given(dealRepository.findByIdAndDeletedAtIsNull(DEAL_ID)).willReturn(Optional.of(deal));

        dealCommand.promoteToQuoteStage(DEAL_ID);

        ArgumentCaptor<DealStageChanged> event = ArgumentCaptor.forClass(DealStageChanged.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().beforeStage()).isEqualTo("LEAD");
        assertThat(event.getValue().afterStage()).isEqualTo("QUOTE");
        assertThat(event.getValue().dealId()).isEqualTo(deal.getId());
        assertThat(event.getValue().companyId()).isEqualTo(deal.getCompanyId());
        assertThat(event.getValue().actor().type()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(event.getValue().actor().actorId()).isNull();
        assertThat(event.getValue().lostReason()).isNull();
    }

    /**
     * <b>이 두 테스트가 이 이슈의 핵심이다.</b> 두 메서드는 멱등이라 이미 도착한 단계에서
     * 다시 불려도 아무 일도 하지 않는데(Q-25 · OD-06), 그때도 발행하면 타임라인에
     * "견적 → 견적" 같은 <b>일어나지 않은 변화</b>가 쌓인다. 견적을 여러 건 발송하는 딜
     * (QT-18)에서는 매 발송마다 한 줄씩 늘어난다.
     */
    @Test
    @DisplayName("이미 견적 단계면 무동작이고 발행도 없다 — 없던 변화를 남기지 않는다")
    void 승급_멱등_미발행() {
        Deal deal = dealAt(Deal.Stage.NEGOTIATION);
        given(dealRepository.findByIdAndDeletedAtIsNull(DEAL_ID)).willReturn(Optional.of(deal));

        dealCommand.promoteToQuoteStage(DEAL_ID);

        assertThat(deal.getStage()).isEqualTo(Deal.Stage.NEGOTIATION);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("성사가 실제로 일어나면 발행하고, 이미 성사면 발행하지 않는다 (OD-06)")
    void 성사_발행과_멱등() {
        Deal 진행중 = dealAt(Deal.Stage.NEGOTIATION);
        given(dealRepository.findByIdAndDeletedAtIsNull(DEAL_ID)).willReturn(Optional.of(진행중));

        dealCommand.markWon(DEAL_ID);

        ArgumentCaptor<DealStageChanged> event = ArgumentCaptor.forClass(DealStageChanged.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().afterStage()).isEqualTo("WON");

        dealCommand.markWon(DEAL_ID);   // 두 번째 전환 — 이미 성사다

        verify(eventPublisher).publishEvent(any(DealStageChanged.class));   // 여전히 한 번뿐
    }
}
