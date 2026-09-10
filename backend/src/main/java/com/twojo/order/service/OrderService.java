package com.twojo.order.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.AuditActor;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.DealCommand;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.QuoteCommand;
import com.twojo.boundary.QuoteQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.error.MissingReferenceException;
import com.twojo.global.response.PageResponse;
import com.twojo.global.sequence.DocumentNumberService;
import com.twojo.global.sequence.DocumentSequence.DocType;
import com.twojo.order.dto.OrderRequests;
import com.twojo.order.dto.OrderResponses;
import com.twojo.order.OrderCreated;
import com.twojo.order.entity.Order;
import com.twojo.order.repository.OrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 전환·조회·일정 기록 (OD-01~10).
 *
 * <p><b>주문에는 담당자 컬럼도 {@code deal_id}도 없다.</b> 범위는 견적과 <b>같은 축</b>에서
 * 파생한다 — {@code deal.assignee_member_id}다 (SC-04, 09 §58·§80). 다만 견적은 자기
 * {@code deal_id}로 한 걸음이면 닿는데, 주문은 <b>quote를 한 번 더 거쳐야</b> 한다.
 * 그 경유는 전부 경계 인터페이스다 (11 §7.3) — {@link QuoteQuery} · {@link DealQuery} ·
 * {@link CustomerQuery}. 범위 밖이거나 없는 대상은 <b>구별 없이 404</b>다 (SC-09).
 *
 * <p><b>전환에서 이 클래스가 하지 않는 것</b>: 견적 상태 판정(승인됨인가)은 quote 모듈의
 * {@link QuoteCommand#lockApprovedForConversion}이, Deal 성사는 {@link DealCommand#markWon}이
 * 한다. 여기서는 <b>주문을 만드는 일</b>과 그 앞뒤의 범위·중복 판정만 맡는다 —
 * 상태 변경의 주체는 언제나 그 상태를 소유한 모듈이다 (11 §7.1).
 *
 * <p>주문 취소는 없다 (OD-11·12 제외, Q-09) — <b>되돌릴 수 없는 생성</b>이라 중복 전환 차단이
 * 이 클래스에서 가장 중요한 판정이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final QuoteQuery quoteQuery;
    private final QuoteCommand quoteCommand;
    private final DealQuery dealQuery;
    private final DealCommand dealCommand;
    private final CustomerQuery customerQuery;
    private final DocumentNumberService documentNumberService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 승인 견적 → 주문 전환 (OD-01~07, Q-25).
     *
     * <p><b>순서가 전부 이유를 가진다.</b>
     * <ol>
     *   <li><b>범위 판정이 먼저다</b> — 남의 딜 견적에 {@code QUOTE_NOT_APPROVED}(409)를
     *       돌려주면 "그 견적은 있는데 아직 승인 전"이라는 사실이 새어 나간다 (SC-09)</li>
     *   <li><b>그다음 잠금 + 승인 검증</b> — 여기서 잡은 행 락이 이 트랜잭션이 커밋할 때까지
     *       유지되어, 뒤이은 중복 검사가 동시 요청 사이에서 직렬화된다</li>
     *   <li><b>중복 검사는 락 안에서</b> — 락 밖이면 여럿이 함께 통과해
     *       {@code orders.quote_id UNIQUE}가 터지고, 그건 409가 아니라 500이다</li>
     *   <li><b>채번은 마지막 검증 뒤에</b> — 앞서 던지면 번호만 소비되고 주문은 안 생긴다.
     *       {@code DocumentNumberService}가 이 트랜잭션에 합류하므로 롤백되면 되돌아가지만,
     *       불필요한 카운터 경합을 만들 이유가 없다 (#72)</li>
     *   <li><b>성사는 주문을 만든 뒤에</b> — 주문 생성이 실패하면 딜만 WON이 된 상태가
     *       남으면 안 된다. 같은 트랜잭션이라 함께 되돌아간다</li>
     * </ol>
     */
    @Transactional
    public OrderResponses.OrderDetail convert(AccessContext ctx, UUID quoteId) {
        QuoteQuery.QuoteOrigin origin = requireQuoteOrigin(ctx.companyId(), quoteId);
        requireDealInScope(ctx, origin.dealId());

        QuoteCommand.ConversionSnapshot snapshot =
                quoteCommand.lockApprovedForConversion(ctx.companyId(), quoteId);   // OD-02 · 행 락
        if (orderRepository.existsByQuoteId(quoteId)) {
            throw new BusinessException(ErrorCode.QUOTE_ALREADY_CONVERTED);   // OD-03
        }

        String orderNo = documentNumberService.next(ctx.companyId(), DocType.ORDER);   // OD-07
        Order order = orderRepository.save(Order.from(ctx.companyId(), snapshot, orderNo));
        dealCommand.markWon(origin.dealId());   // OD-06 — 진행 중이면 단계 무관, 이미 성사면 무동작

        // 전환 감사 이벤트 (AC-07, #22). 자동 성사가 함께 일어난 경우 같은 트랜잭션에서
        // DealStageChanged(SYSTEM)도 발행되는데, 그건 markWon 안쪽 몫이라 여기서 손대지 않는다.
        eventPublisher.publishEvent(new OrderCreated(order.getCompanyId(), order.getId(),
                origin.dealId(), order.getOrderNo(), quoteId,
                AuditActor.member(ctx.memberId()), Instant.now()));

        // 단계를 다시 읽어 응답에 싣는다 — 방금 일으킨 자동 성사를 응답이 부정하면 안 된다.
        // 명시적 flush는 필요 없다: dealStage는 엔티티가 아니라 쿼리(summariesByIds)로 읽고,
        // JPA FlushModeType.AUTO가 쿼리 실행 전 flush를 보장한다. 응답을 엔티티에서 만들었던
        // QuoteService.update와는 사정이 다르다 (거기서는 flush가 있어야 version이 최신이 된다).
        DealQuery.DealSummary deal = requireDealInScope(ctx, origin.dealId());
        return OrderResponses.OrderDetail.of(order, originOf(origin, deal,
                customerNamesOf(ctx.companyId(), List.of(deal.customerId()))));
    }

    /**
     * 목록 (OD-08) — 전환일 기준 기간 필터. 날짜는 <b>한국 날짜로</b> 끊고 {@code to}는 그날을 포함한다.
     *
     * <p>영업(OWNED_ONLY)은 <b>담당 Deal의 주문만</b> 본다 (SC-04). 담당 Deal이 하나도 없으면
     * 빈 목록이다 — {@code OrderSpecs}가 빈 집합을 거짓 조건으로 만든다.
     * 기업 관리자는 회사 전체다 (SC-05).
     */
    public PageResponse<OrderResponses.OrderRow> list(AccessContext ctx, LocalDate from, LocalDate to,
                                                      Pageable pageable) {
        Collection<UUID> visibleQuoteIds = ctx.scope() == AccessScope.OWNED_ONLY
                ? quoteQuery.quoteIdsByDeals(ctx.companyId(),
                        dealQuery.assignedDealIds(ctx.companyId(), ctx.memberId()))
                : null;   // null = 제한 없음

        Page<Order> page = orderRepository.search(ctx.companyId(),
                OrderPeriod.startOfDay(from), OrderPeriod.startOfNextDay(to), visibleQuoteIds, pageable);

        Map<UUID, QuoteQuery.QuoteOrigin> originByQuote = quoteQuery
                .originsByIds(ctx.companyId(), page.getContent().stream().map(Order::getQuoteId).toList())
                .stream().collect(Collectors.toMap(QuoteQuery.QuoteOrigin::quoteId, Function.identity()));
        Map<UUID, DealQuery.DealSummary> dealById = dealQuery
                .summariesByIds(ctx.companyId(),
                        originByQuote.values().stream().map(QuoteQuery.QuoteOrigin::dealId).distinct().toList())
                .stream().collect(Collectors.toMap(DealQuery.DealSummary::id, Function.identity()));

        // 고객사 이름도 배치로 한 번에 (#273) — 이제 이 목록의 조회 횟수가 줄 수와 무관해진다.
        // 관리자는 네 번(주문 페이지 · 견적 출처 · 딜 요약 · 고객사 이름), 영업은 범위를 좁히는
        // assignedDealIds·quoteIdsByDeals가 앞에 붙어 여섯 번이다.
        Map<UUID, String> customerNames = customerNamesOf(ctx.companyId(),
                dealById.values().stream().map(DealQuery.DealSummary::customerId).toList());

        return PageResponse.from(page.map(order -> {
            QuoteQuery.QuoteOrigin origin = originByQuote.get(order.getQuoteId());
            if (origin == null) {
                throw new MissingReferenceException("quote", order.getQuoteId());   // FK가 보장하는 자리 (#167)
            }
            return OrderResponses.OrderRow.of(order,
                    originOf(origin, requireFound(dealById.get(origin.dealId())), customerNames));
        }));
    }

    /** 상세 (OD-09) — 스냅샷 항목 포함. {@code dealId}는 quote를 거쳐 붙인다 */
    public OrderResponses.OrderDetail get(AccessContext ctx, UUID orderId) {
        ScopedOrder scoped = findInScope(ctx, orderId);
        return OrderResponses.OrderDetail.of(scoped.order(), scoped.origin());
    }

    /**
     * 착수일·납기 기록 (OD-10) — <b>상태 전이가 아니다</b> (전이표 §8).
     * <p>주문에 {@code @Version}이 없어 낙관적 락도 없다 — 상태가 없으니 경합으로 뒤집힐 전이가 없다.
     */
    @Transactional
    public OrderResponses.OrderDetail updateSchedule(AccessContext ctx, UUID orderId,
                                                     OrderRequests.UpdateSchedule request) {
        ScopedOrder scoped = findInScope(ctx, orderId);
        scoped.order().updateSchedule(request.startDate(), request.deliveryDate());
        return OrderResponses.OrderDetail.of(scoped.order(), scoped.origin());
    }

    /**
     * 회사 스코프 + 담당 축으로 주문 한 건을 찾는다. 없으면 404 —
     * <b>존재와 권한을 구별하지 않는다</b> (SC-09).
     */
    private ScopedOrder findInScope(AccessContext ctx, UUID orderId) {
        Order order = orderRepository.findWithItemsByIdAndCompanyId(orderId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        QuoteQuery.QuoteOrigin origin = originOfOrder(ctx.companyId(), order.getQuoteId());
        DealQuery.DealSummary deal = requireDealInScope(ctx, origin.dealId());
        return new ScopedOrder(order, originOf(origin, deal,
                customerNamesOf(ctx.companyId(), List.of(deal.customerId()))));
    }

    /**
     * 범위 판정을 통과한 주문과 그 부가 정보.
     *
     * <p>함께 들고 다니는 이유는 {@code QuoteService.ScopedQuote}와 같다 — 범위 판정이 이미
     * 견적·Deal·고객사를 다 읽으므로, 응답을 만드는 데 드는 추가 조회가 없다.
     */
    private record ScopedOrder(Order order, OrderResponses.Origin origin) {
    }

    /**
     * 주문이 스스로 답할 수 없는 값 — 견적번호·딜·고객사.
     *
     * <p><b>조회를 하지 않는다.</b> 고객사 id는 {@code DealSummary}가 싣고(#273), 이름은 호출자가
     * {@code namesByIds}로 한 번에 받아 넘긴다. 예전에는 여기서 줄마다 두 번 조회했다 —
     * {@code customerIdOf}로 딜→고객사를 되짚고 {@code get}으로 이름을 읽었다.
     *
     * <p>이름이 <b>null일 수 있다</b> — 지워진 고객사는 배치 결과에서 빠진다. 단건 경로도 같은
     * 창구를 타므로 상세·전환 응답에서도 그렇다. 주문이 있는데 고객사만 지워진 상태는
     * 실제로 가능하다 — 삭제는 <b>진행 중</b> 딜만 막고(hasOpenDeals), 성사된 딜은 막지 않는다.
     */
    private OrderResponses.Origin originOf(QuoteQuery.QuoteOrigin origin, DealQuery.DealSummary deal,
                                           Map<UUID, String> customerNames) {
        return new OrderResponses.Origin(origin.quoteNo(), deal.id(), deal.title(), deal.stage(),
                deal.customerId(), customerNames.get(deal.customerId()));
    }

    /**
     * 고객사 id 묶음 → 이름 (SC-01). 단건 경로는 원소 하나짜리 묶음으로 같은 창구를 탄다 —
     * 두 경로가 이름을 다르게 얻으면 "목록엔 있는데 상세엔 없는" 차이가 생긴다.
     */
    private Map<UUID, String> customerNamesOf(UUID companyId, Collection<UUID> customerIds) {
        return customerQuery.namesByIds(companyId, customerIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(CustomerQuery.CustomerSummary::id,
                        CustomerQuery.CustomerSummary::name));
    }

    /**
     * <b>요청이 지목한</b> 견적 — 사용자가 준 id라 없거나 다른 회사면 404다 (SC-01·09).
     * 전환({@code convert})만 이 경로를 쓴다.
     */
    private QuoteQuery.QuoteOrigin requireQuoteOrigin(UUID companyId, UUID quoteId) {
        return quoteQuery.originsByIds(companyId, List.of(quoteId)).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * <b>주문이 가리키는</b> 견적 — 없으면 404가 아니라 <b>데이터 이상</b>이다 (#167).
     *
     * <p>{@code orders.quote_id}는 NOT NULL FK이고 {@code quote}에는 소프트 삭제가 없다
     * (11 §1.5 — 소프트 삭제 대상은 customer·deal·activity뿐). 즉 <b>행이 사라질 수 없는 자리</b>라,
     * 여기서 404를 던지면 멀쩡한 주문이 "없거나 권한 없음"으로 보이고 원인이 견적 쪽이라는
     * 단서가 응답에도 로그에도 남지 않는다 — {@code MissingReferenceException}이 500으로 바꾸며
     * 스택을 남긴다.
     *
     * <p>같은 조회라도 {@link #requireQuoteOrigin}은 404다. <b>id의 출처가 다르기 때문이다</b> —
     * 저쪽은 사용자가 URL로 준 값이라 없는 것이 정상 시나리오다.
     */
    private QuoteQuery.QuoteOrigin originOfOrder(UUID companyId, UUID quoteId) {
        return quoteQuery.originsByIds(companyId, List.of(quoteId)).stream().findFirst()
                .orElseThrow(() -> new MissingReferenceException("quote", quoteId));
    }

    /**
     * Deal이 이 요청의 범위 안에 있는지 — 회사가 다르거나 담당이 아니면 404 (SC-01·02·09).
     * {@code QuoteService.requireDealInScope}와 같은 판정이다 — 주문의 범위 축도 Deal이다.
     */
    private DealQuery.DealSummary requireDealInScope(AccessContext ctx, UUID dealId) {
        DealQuery.DealSummary deal = requireFound(dealQuery
                .summariesByIds(ctx.companyId(), List.of(dealId)).stream().findFirst().orElse(null));
        if (ctx.scope() == AccessScope.OWNED_ONLY
                && !ctx.memberId().equals(dealQuery.assigneeIdOf(dealId))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return deal;
    }

    /**
     * Deal이 안 보이면 404다 — <b>{@code MissingReferenceException}(500)이 아니다</b>.
     *
     * <p>#167이 나눈 두 층위 중 이쪽은 <b>보이지 않는 것</b>이다. {@code quote.deal_id}가 FK라
     * <b>행 자체는 반드시 있고</b>, {@code DealQuery.summariesByIds}가 빼는 것은 <b>소프트 삭제된</b>
     * Deal이다 (11 §1.5). 행의 부재가 아니라 가시성 규칙이므로 "범위 밖인지 없는지 구별하지 않는다"는
     * SC-09의 404가 맞다 — 주문이 가리키는 견적({@link #originOfOrder})과 다른 이유가 여기 있다.
     *
     * <p>지금 도달할 수 없는 이유는 <b>Deal 삭제(DL-16)가 아직 구현되지 않아서</b>이고, 구현된
     * 뒤에는 DL-17("견적이 연결된 Deal은 삭제할 수 없다", {@code DEAL_HAS_QUOTES}가 이미 예약돼
     * 있다)이 유일한 방어선이 된다 — 주문이 있으면 견적도 반드시 있기 때문이다.
     *
     * <p>조용히 null을 흘려보내지 않는 이유는, 그러면 dealTitle이 빈 주문 행이 화면에 나가서다.
     * <b>다만 목록에서는 한 줄의 이상이 페이지 전체를 404로 만든다</b> — DL-16을 구현할 때
     * 이 자리를 함께 봐야 한다.
     */
    private static <T> T requireFound(T value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return value;
    }


    /** {@code to}는 그날을 <b>포함</b>한다 — 다음 날 0시 미만으로 끊는다 */
}
