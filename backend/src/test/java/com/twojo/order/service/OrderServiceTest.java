package com.twojo.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.AuditActorType;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.DealCommand;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.QuoteCommand;
import com.twojo.boundary.QuoteCommand.ConversionSnapshot;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.error.MissingReferenceException;
import com.twojo.global.sequence.DocumentNumberService;
import com.twojo.global.sequence.DocumentSequence.DocType;
import com.twojo.order.dto.OrderResponses;
import com.twojo.order.OrderCreated;
import com.twojo.order.entity.Order;
import com.twojo.order.repository.OrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private OrderService orderService;

    @Captor private ArgumentCaptor<Instant> 하한;
    @Captor private ArgumentCaptor<Instant> 상한;

    /** 기업 관리자 — 범위 제한이 없어 기간 변환만 남는다 (SC-05) */
    private final AccessContext 관리자 =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);

    private static QuoteQuery.QuoteOrigin 출처() {
        return new QuoteQuery.QuoteOrigin(QUOTE_ID, "Q-2609-001", DEAL_ID);
    }

    private static DealQuery.DealSummary 딜(String stage) {
        return new DealQuery.DealSummary(DEAL_ID, CUSTOMER_ID, "도담 사무가구", stage, 1_000_000L, null, Instant.now());
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
        given(customerQuery.namesByIds(COMPANY_ID, List.of(CUSTOMER_ID)))
                .willReturn(List.of(new CustomerQuery.CustomerSummary(CUSTOMER_ID, "도담산업")));
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

    /**
     * 전환 감사 이벤트 (AC-07, #22). 행위자는 전환을 요청한 <b>구성원</b>이다 —
     * 같은 트랜잭션에서 {@code markWon}이 일으키는 {@code DealStageChanged}는 SYSTEM이라
     * 두 사건의 주체가 갈린다. {@code quoteId}가 실려 주문과 출처 견적이 이어진다.
     */
    @Test
    @DisplayName("전환에 성공하면 OrderCreated(MEMBER)를 발행한다")
    void 전환_발행() {
        전환_가능한_견적();
        given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID)))
                .willReturn(List.of(딜("NEGOTIATION")), List.of(딜("WON")));
        given(customerQuery.namesByIds(COMPANY_ID, List.of(CUSTOMER_ID)))
                .willReturn(List.of(new CustomerQuery.CustomerSummary(CUSTOMER_ID, "도담산업")));
        given(documentNumberService.next(COMPANY_ID, DocType.ORDER)).willReturn("O-2609-001");
        given(orderRepository.save(any(Order.class))).willAnswer(call -> call.getArgument(0));

        orderService.convert(ctx, QUOTE_ID);

        ArgumentCaptor<OrderCreated> event = ArgumentCaptor.forClass(OrderCreated.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().orderNo()).isEqualTo("O-2609-001");
        assertThat(event.getValue().quoteId()).isEqualTo(QUOTE_ID);
        assertThat(event.getValue().dealId()).isEqualTo(DEAL_ID);
        assertThat(event.getValue().companyId()).isEqualTo(COMPANY_ID);
        assertThat(event.getValue().actor().type()).isEqualTo(AuditActorType.MEMBER);
        assertThat(event.getValue().actor().actorId()).isEqualTo(MEMBER_ID);
    }

    /** 주문이 만들어지지 않은 요청은 사건이 아니다 — 없는 주문이 타임라인에 남으면 안 된다 */
    @Test
    @DisplayName("이미 전환된 견적은 QUOTE_ALREADY_CONVERTED — 채번도 성사도 발행도 일어나지 않는다 (OD-03)")
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
        verify(eventPublisher, never()).publishEvent(any(Object.class));
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
        verify(eventPublisher, never()).publishEvent(any(Object.class));
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

    /**
     * 기간은 <b>한국 날짜로</b> 끊는다 (OD-08). 서버 시간대로 끊으면 KST 자정 부근의 주문이
     * 하루 어긋난 칸에 들어간다 — 채번의 연월 판정과 같은 이유다 (#72).
     *
     * <p><b>{@code to}는 그날을 포함한다.</b> 사람이 "9/1~9/8"이라고 쓸 때 9/8의 주문을 빼는
     * 필터는 쓰는 사람의 뜻과 다르다. 그래서 상한은 <b>다음 날 0시 미만</b>으로 만든다 —
     * {@code to}일 0시로 끊으면 그날 주문이 통째로 사라진다.
     *
     * <p><b>여기서 보는 것은 서비스가 만든 경계값까지다.</b> {@code OrderSpecs}가 그 값을
     * {@code >=} / {@code <}로 쓰는지는 이 테스트가 보지 않는다 — 상한을 {@code <=}로 쓰면
     * 다음 날 0시 정각 주문 한 건이 더 딸려 들어온다.
     */
    @Test
    @DisplayName("기간 필터는 KST 경계로 변환된다 — to는 그날을 포함한다 (OD-08)")
    void 기간_필터는_KST_경계로_변환된다() {
        given(orderRepository.search(any(), any(), any(), any(), any())).willReturn(Page.<Order>empty());

        orderService.list(관리자, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8), PageRequest.of(0, 20));

        verify(orderRepository).search(any(), 하한.capture(), 상한.capture(), any(), any());
        assertThat(하한.getValue()).isEqualTo(Instant.parse("2026-08-31T15:00:00Z"));   // 9/1 00:00 KST
        assertThat(상한.getValue()).isEqualTo(Instant.parse("2026-09-08T15:00:00Z"));   // 9/9 00:00 KST (제외)
    }

    /**
     * 비운 쪽만 조건에서 빠진다 — <b>한쪽만 주는 것이 정상 사용</b>이다 ("9월 이후 전부" 같은).
     * 빈 값을 오늘이나 epoch 같은 기본값으로 채우면 사용자가 걸지 않은 조건이 생긴다.
     */
    @Test
    @DisplayName("비운 쪽만 조건에서 빠진다 — 한쪽만 비운 경우와 둘 다 비운 경우")
    void 비운_쪽만_조건에서_빠진다() {
        given(orderRepository.search(any(), any(), any(), any(), any())).willReturn(Page.<Order>empty());

        orderService.list(관리자, null, LocalDate.of(2026, 9, 8), PageRequest.of(0, 20));   // from만 비움
        orderService.list(관리자, null, null, PageRequest.of(0, 20));                       // 둘 다 비움

        verify(orderRepository, times(2)).search(any(), 하한.capture(), 상한.capture(), any(), any());
        assertThat(하한.getAllValues()).containsExactly(null, null);
        assertThat(상한.getAllValues())
                .containsExactly(Instant.parse("2026-09-08T15:00:00Z"), null);
    }

    /**
     * <b>같은 조회, 다른 층위</b> (#167). 견적을 못 찾았을 때의 답이 <b>id의 출처</b>에 따라 갈린다.
     *
     * <ul>
     *   <li>전환 — 사용자가 URL로 준 id다. 없는 것이 정상 시나리오라 <b>404</b> (SC-09)</li>
     *   <li>상세 — {@code orders.quote_id}(NOT NULL FK)에서 온 id이고 {@code quote}에는 소프트
     *       삭제가 없다. 사라질 수 없는 자리라 <b>데이터 이상</b>이고, 404로 내보내면 멀쩡한 주문이
     *       "없거나 권한 없음"으로 보이며 원인 단서가 사라진다</li>
     * </ul>
     *
     * <p>둘을 한 테스트에 둔 이유는, 나중에 누가 두 경로를 하나로 합치면 <b>여기서 바로 깨지게</b>
     * 하기 위해서다.
     */
    @Test
    @DisplayName("견적 부재의 답이 id 출처에 따라 갈린다 — 전환은 404, 주문 상세는 무결성 이상 (#167)")
    void 견적_부재는_출처에_따라_층위가_다르다() {
        given(quoteQuery.originsByIds(COMPANY_ID, List.of(QUOTE_ID))).willReturn(List.of());

        // 전환 — 사용자가 지목한 견적이 없다
        assertThatThrownBy(() -> orderService.convert(ctx, QUOTE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(OrderServiceTest::errorOf)
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        // 상세 — 주문은 있는데 그 주문이 가리키는 견적이 없다
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findWithItemsByIdAndCompanyId(orderId, COMPANY_ID))
                .willReturn(Optional.of(Order.from(COMPANY_ID, 스냅샷(), "O-2609-001")));

        assertThatThrownBy(() -> orderService.get(ctx, orderId))
                .isInstanceOf(MissingReferenceException.class)   // 폴백 핸들러가 500 + 스택
                .isNotInstanceOf(BusinessException.class);
    }
}
