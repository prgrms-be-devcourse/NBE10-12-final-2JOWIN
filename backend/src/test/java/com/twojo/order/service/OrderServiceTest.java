package com.twojo.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.DealCommand;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.QuoteCommand;
import com.twojo.boundary.QuoteCommand.ConversionSnapshot;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.sequence.DocumentNumberService;
import com.twojo.global.sequence.DocumentSequence.DocType;
import com.twojo.order.dto.OrderResponses;
import com.twojo.order.entity.Order;
import com.twojo.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 주문 전환 — <b>서비스가 더하는 것</b>을 본다: 판정의 <b>순서</b>, 중복 차단, 자동 성사 위임.
 *
 * <p>견적이 승인됐는지는 quote 모듈이 보고(전이표 §6), 딜을 성사로 옮기는 규칙은 deal 모듈이
 * 본다(§5). 여기서 그걸 다시 검증하면 판정이 두 곳으로 갈린다 — 대신 <b>제대로 위임하는가</b>와
 * <b>부르는 차례가 맞는가</b>를 고정한다.
 *
 * <p><b>순서가 왜 검증 대상인가.</b> 남의 딜 견적에 409({@code QUOTE_NOT_APPROVED})가 나가면
 * "그 견적은 있는데 아직 승인 전"이라는 사실이 새어 나간다 (SC-09). 범위 판정이 먼저라는 것은
 * 성능 문제가 아니라 <b>노출 문제</b>다.
 *
 * <p>동시 전환은 여기서 볼 수 없다 — 행 락과 UNIQUE가 실제 DB에서만 작동하므로
 * {@code OrderConversionConcurrencyTest}가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();

    private final AccessContext ctx =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private OrderRepository orderRepository;
    @Mock private QuoteQuery quoteQuery;
    @Mock private QuoteCommand quoteCommand;
    @Mock private DealQuery dealQuery;
    @Mock private DealCommand dealCommand;
    @Mock private CustomerQuery customerQuery;
    @Mock private DocumentNumberService documentNumberService;

    @InjectMocks private OrderService orderService;

    private static QuoteQuery.QuoteOrigin 출처() {
        return new QuoteQuery.QuoteOrigin(QUOTE_ID, "Q-2609-001", DEAL_ID);
    }

    private static DealQuery.DealSummary 딜(String stage) {
        return new DealQuery.DealSummary(DEAL_ID, "도담 사무가구", stage, 1_000_000L, null, Instant.now());
    }

    private static ConversionSnapshot 스냅샷() {
        return new ConversionSnapshot(QUOTE_ID, "Q-2609-001", DEAL_ID,
                1_000_000L, 100_000L, 1_100_000L,
                List.of(new ConversionSnapshot.Line("사무용 의자", "EA", 10, 80_000L, 800_000L)));
    }

    /** 견적이 있고, 담당 딜이고, 승인된 상태 — 여기서 갈라지는 것만 각 테스트가 덮어쓴다 */
    private void 전환_가능한_견적() {
        given(quoteQuery.originsByIds(COMPANY_ID, List.of(QUOTE_ID))).willReturn(List.of(출처()));
        given(dealQuery.assigneeIdOf(DEAL_ID)).willReturn(MEMBER_ID);
        given(quoteCommand.lockApprovedForConversion(COMPANY_ID, QUOTE_ID)).willReturn(스냅샷());
    }

    private static ErrorCode errorOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("전환하면 채번한 주문이 저장되고 Deal 성사가 위임된다 (OD-06·07)")
    void 전환() {
        전환_가능한_견적();
        given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID)))
                .willReturn(List.of(딜("NEGOTIATION")), List.of(딜("WON")));   // markWon 뒤 다시 읽는다
        given(dealQuery.customerIdOf(DEAL_ID)).willReturn(CUSTOMER_ID);
        given(customerQuery.get(ctx, CUSTOMER_ID)).willReturn(new CustomerQuery.CustomerSummary(CUSTOMER_ID, "도담산업"));
        given(documentNumberService.next(COMPANY_ID, DocType.ORDER)).willReturn("O-2609-001");
        given(orderRepository.save(any(Order.class))).willAnswer(call -> call.getArgument(0));

        OrderResponses.OrderDetail response = orderService.convert(ctx, QUOTE_ID);

        verify(dealCommand).markWon(DEAL_ID);
        assertThat(response.orderNo()).isEqualTo("O-2609-001");
        assertThat(response.quoteNo()).isEqualTo("Q-2609-001");
        assertThat(response.dealId()).isEqualTo(DEAL_ID);
        assertThat(response.customerName()).isEqualTo("도담산업");
        assertThat(response.totalAmount()).isEqualTo(1_100_000L);
        assertThat(response.items()).hasSize(1);

        // 응답이 방금 일으킨 자동 성사를 반영해야 한다 — 전환 전 단계가 나가면 화면이 Deal을 다시 조회한다
        assertThat(response.dealStage()).isEqualTo("WON");
    }

    @Test
    @DisplayName("이미 전환된 견적은 QUOTE_ALREADY_CONVERTED — 채번도 성사도 일어나지 않는다 (OD-03)")
    void 재전환_차단() {
        전환_가능한_견적();
        given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID))).willReturn(List.of(딜("WON")));
        given(orderRepository.existsByQuoteId(QUOTE_ID)).willReturn(true);

        assertThatThrownBy(() -> orderService.convert(ctx, QUOTE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(OrderServiceTest::errorOf)
                .isEqualTo(ErrorCode.QUOTE_ALREADY_CONVERTED);

        // 번호를 소비하고 던지면 그 번호는 영영 빈자리로 남는다 (#72 — 롤백되긴 하지만 경합을 만들 이유가 없다)
        verify(documentNumberService, never()).next(any(), any());
        verify(orderRepository, never()).save(any());
        verify(dealCommand, never()).markWon(any());
    }

    /**
     * <b>이 테스트가 지키는 것은 순서다.</b> 승인 검증이 먼저 일어나면 남의 딜 견적에 409가 나가
     * "그 견적은 있는데 아직 승인 전"이 드러난다 (SC-09).
     */
    @Test
    @DisplayName("담당이 아닌 Deal의 견적은 404 — 승인 여부를 보기 전에 막힌다 (SC-04·09)")
    void 범위_밖은_승인_검증_전에_막힌다() {
        given(quoteQuery.originsByIds(COMPANY_ID, List.of(QUOTE_ID))).willReturn(List.of(출처()));
        given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID))).willReturn(List.of(딜("NEGOTIATION")));
        given(dealQuery.assigneeIdOf(DEAL_ID)).willReturn(UUID.randomUUID());   // 다른 사람 담당

        assertThatThrownBy(() -> orderService.convert(ctx, QUOTE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(OrderServiceTest::errorOf)
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(quoteCommand, never()).lockApprovedForConversion(any(), any());
    }

    @Test
    @DisplayName("없거나 다른 회사의 견적은 404 (SC-01·09)")
    void 없는_견적() {
        given(quoteQuery.originsByIds(COMPANY_ID, List.of(QUOTE_ID))).willReturn(List.of());

        assertThatThrownBy(() -> orderService.convert(ctx, QUOTE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(OrderServiceTest::errorOf)
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(quoteCommand, never()).lockApprovedForConversion(any(), any());
    }

    /**
     * 상태 판정은 엔티티의 몫이라 여기서 다시 하지 않는다 — 다만 <b>삼키지 않는지</b>는 봐야 한다.
     * 삼키면 승인되지 않은 견적이 주문이 되고, 주문은 되돌릴 수 없다 (취소 없음, Q-09).
     */
    @Test
    @DisplayName("미승인 견적의 예외는 그대로 전파된다 — 서비스가 삼키지 않는다 (OD-02)")
    void 미승인_예외_전파() {
        given(quoteQuery.originsByIds(COMPANY_ID, List.of(QUOTE_ID))).willReturn(List.of(출처()));
        given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID))).willReturn(List.of(딜("QUOTE")));
        given(dealQuery.assigneeIdOf(DEAL_ID)).willReturn(MEMBER_ID);
        given(quoteCommand.lockApprovedForConversion(COMPANY_ID, QUOTE_ID))
                .willThrow(new BusinessException(ErrorCode.QUOTE_NOT_APPROVED));

        assertThatThrownBy(() -> orderService.convert(ctx, QUOTE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(OrderServiceTest::errorOf)
                .isEqualTo(ErrorCode.QUOTE_NOT_APPROVED);

        verify(orderRepository, never()).save(any());
        verify(dealCommand, never()).markWon(any());
    }

    /**
     * <b>담당 Deal이 하나도 없는 영업</b>이 회사 전체 주문을 보면 SC-04가 통째로 뚫린다.
     * 빈 집합이 "제한 없음(null)"으로 새지 않는지를 여기서 고정한다 — 거짓 조건 변환은
     * {@code OrderSpecs}가 하지만, <b>null을 넘기지 않는 것</b>은 서비스의 책임이다.
     */
    @Test
    @DisplayName("담당 Deal이 없는 영업의 목록은 빈 집합으로 좁혀진다 — 제한 없음이 아니다 (SC-04)")
    void 담당_없는_영업의_범위() {
        given(dealQuery.assignedDealIds(COMPANY_ID, MEMBER_ID)).willReturn(List.of());
        given(quoteQuery.quoteIdsByDeals(COMPANY_ID, List.of())).willReturn(List.of());
        given(orderRepository.search(any(), any(), any(), anyList(), any()))
                .willReturn(Page.<Order>empty());

        assertThat(orderService.list(ctx, null, null, PageRequest.of(0, 20)).content()).isEmpty();
    }
}
