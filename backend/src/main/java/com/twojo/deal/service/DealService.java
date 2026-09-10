package com.twojo.deal.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.AuditActor;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.OrderQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.Role;
import com.twojo.deal.DealStageChanged;
import com.twojo.deal.dto.DealRequests;
import com.twojo.deal.dto.DealResponses;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deal 생성·조회·수정 (DL-01~06·13~15).
 *
 * <p><b>범위 판정은 {@code ctx.scope()}만 읽는다</b> — Role→Scope 변환은 인증 필터가 이미 했다
 * (PR #30). 양쪽에서 판정하면 규칙이 바뀔 때 한쪽만 고쳐진다.
 *
 * <p>타 도메인 참조는 전부 경계 인터페이스 경유다 (11 §7.3) — 고객사는 {@link CustomerQuery},
 * 구성원은 {@link MemberQuery}. 참조 ID가 같은 회사 소속이 아니면 403이 아니라
 * <b>404</b>다 (SC-09, 검증 노트 #3).
 *
 * <p>단계 전이(advance·revert·lose·reopen)는 이 클래스에 없다 — 전이표 §5를 엔티티 메서드로
 * 옮기는 작업이라 별도 이슈로 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealService {

    private final DealRepository dealRepository;
    private final CustomerQuery customerQuery;
    private final MemberQuery memberQuery;
    private final QuoteQuery quoteQuery;
    private final OrderQuery orderQuery;
    private final ApplicationEventPublisher eventPublisher;

    /** 생성 (DL-01~04) — 회사의 모든 고객사에 가능하고, 배정 대상은 활성 구성원이다 */
    @Transactional
    public DealResponses.DealItem create(AccessContext ctx, DealRequests.CreateDeal request) {
        String customerName = customerQuery.get(ctx, request.customerId()).name();   // 없으면 404

        UUID assigneeId = request.assigneeMemberId() == null ? ctx.memberId() : request.assigneeMemberId();
        String assigneeName = requireActiveMemberName(ctx, assigneeId);

        Deal deal = dealRepository.save(Deal.create(ctx.companyId(), request.customerId(), assigneeId,
                request.title(), request.expectedAmount(), request.dueDate()));

        return DealResponses.DealItem.of(deal, customerName, assigneeName);
    }

    /**
     * 목록·보드 (DL-06·13·14).
     *
     * <p>영업(OWNED_ONLY)은 요청에 assigneeId를 무엇으로 넣든 <b>본인 담당만</b> 본다 (SC-02) —
     * 필터를 무시하는 게 아니라 범위가 그렇게 정의돼 있다. 기업 관리자는 회사 전체다 (SC-05).
     */
    public PageResponse<DealResponses.DealItem> list(AccessContext ctx, Deal.Stage stage,
                                                     UUID assigneeId, UUID customerId, Pageable pageable) {
        UUID scopedAssigneeId = ctx.scope() == AccessScope.OWNED_ONLY ? ctx.memberId() : assigneeId;

        Page<Deal> deals = dealRepository.search(ctx.companyId(), stage, scopedAssigneeId, customerId, pageable);

        // 고객사 이름은 배치로 한 번에 받는다 (#273) — 줄마다 부르면 20건짜리 목록에 조회가 20번이다.
        // 담당자 이름은 아직 줄마다다: MemberQuery에 이름 배치 창구가 없고, findAllActive로는
        // 비활성 담당자의 이름이 사라진다(MB-14로 이관되는 것은 진행 중 딜뿐이다). A 창구가 열리면 여기도 고친다.
        Map<UUID, String> customerNames = customerNamesOf(ctx.companyId(),
                deals.getContent().stream().map(Deal::getCustomerId).toList());

        return PageResponse.from(deals.map(deal -> DealResponses.DealItem.of(deal,
                customerNames.get(deal.getCustomerId()),
                memberQuery.get(deal.getAssigneeMemberId()).name())));
    }

    /**
     * 상세 (DL-15·18) — 견적·주문 요약과 성사 금액을 함께 싣는다.
     *
     * <p><b>세 창구를 지난다</b> — 견적·주문은 C 소유의 다른 모듈이라 직접 읽지 않는다.
     * {@code briefsByDeals}로 견적을 얻고, 그 id로 {@code briefsByQuotes}를 물어 주문을 얻는다.
     * 주문에는 {@code deal_id}가 없어 <b>견적을 한 홉 지나야</b> 딜에 닿기 때문이다.
     *
     * <p><b>성사 금액은 주문 합계다</b> (DL-18) — 예상 금액이 아니다.
     * 이미 얻은 주문 줄을 더한다. {@code OrderQuery.wonTotalsByQuotes}를 따로 부르지 않는 이유는
     * 같은 행을 두 번 읽게 되기 때문이다 — 그 창구는 기간 집계(DB-02·06)가 쓰는 자리다.
     *
     * <p><b>성사 전에는 null이다.</b> 화면 규칙이 "성사 전 expectedAmount, 성사 후 wonAmount"라
     * (08 {@code DealDetail} javadoc), 진행 중인 딜에 0을 넣으면 "주문이 0원"으로 읽힌다.
     * 성사인데 주문이 없는 경우는 없다 — 성사는 주문 전환만이 만든다 (DL-09).
     */
    public DealResponses.DealDetail get(AccessContext ctx, UUID dealId) {
        Deal deal = findInScope(ctx, dealId);

        List<QuoteQuery.QuoteBrief> quotes = quoteQuery.briefsByDeals(ctx.companyId(), List.of(dealId));
        List<OrderQuery.OrderBrief> orders = orderQuery.briefsByQuotes(ctx.companyId(),
                quotes.stream().map(QuoteQuery.QuoteBrief::id).toList());

        Long wonAmount = deal.getStage() == Deal.Stage.WON
                ? orders.stream().mapToLong(OrderQuery.OrderBrief::totalAmount).sum()
                : null;

        return DealResponses.DealDetail.of(deal,
                customerQuery.get(ctx, deal.getCustomerId()).name(),
                memberQuery.get(deal.getAssigneeMemberId()).name(),
                quotes, orders, wonAmount);
    }

    /** 제목·예상 금액·마감일 수정 (DL-02·03) — null 필드는 변경하지 않는다 */
    @Transactional
    public DealResponses.DealItem update(AccessContext ctx, UUID dealId, DealRequests.UpdateDeal request) {
        Deal deal = findInScope(ctx, dealId);
        deal.checkVersion(request.version());
        deal.update(request.title(), request.expectedAmount(), request.dueDate());

        return DealResponses.DealItem.of(deal,
                customerQuery.get(ctx, deal.getCustomerId()).name(),
                memberQuery.get(deal.getAssigneeMemberId()).name());
    }

    /**
     * 삭제 (DL-16) — 소프트 삭제다. 견적이 하나라도 연결돼 있으면 막는다 (DL-17).
     *
     * <p><b>단계는 보지 않는다</b> — DL-16이 상태를 제한하지 않고, 종결(WON·LOST) Deal도 지울 수 있다.
     * 실제로 막는 축은 견적 연결 하나뿐인데, 성사한 Deal은 견적을 거쳐 왔으므로 DL-17에서 자연히 걸린다.
     *
     * <p><b>{@code version}을 받지 않는다</b> — 07 §C가 이 행에만 "version 포함"을 적지 않았다
     * (PATCH 수정·담당자 변경과 다른 자리다). 덮어쓸 필드가 없어 잃을 수정이 없다 —
     * 같은 이유로 {@code CustomerService.delete}도 받지 않는다 (CU-07).
     *
     * <p>판정은 {@link QuoteQuery#quoteIdsByDeals}로 한다 — 견적은 C 소유라 직접 읽지 않는다.
     * 단건이라 묶음에 이 Deal 하나만 넘기고, 빈 목록이면 연결된 견적이 없다는 뜻이다.
     */
    @Transactional
    public void delete(AccessContext ctx, UUID dealId, Instant now) {
        Deal deal = findInScope(ctx, dealId);

        if (!quoteQuery.quoteIdsByDeals(ctx.companyId(), List.of(dealId)).isEmpty()) {
            throw new BusinessException(ErrorCode.DEAL_HAS_QUOTES);
        }
        deal.softDelete(now);
    }

    /**
     * 담당자 변경 (DL-05, SC-06) — <b>기업 관리자 전용</b>이다.
     *
     * <p>역할 판정을 컨트롤러가 아니라 여기서 한다 — 다른 호출 경로가 생겼을 때
     * 웹 계층에만 걸린 검사는 그대로 뚫린다. 역할로 갈리는 행위의 실패는 404가 아니라
     * <b>403 FORBIDDEN</b>이다 (Q-43, 권한 매트릭스의 ⭕/✕ 층).
     */
    @Transactional
    public DealResponses.DealItem changeAssignee(AccessContext ctx, UUID dealId,
                                                 DealRequests.ChangeAssignee request) {
        if (ctx.role() != Role.COMPANY_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Deal deal = findInScope(ctx, dealId);
        deal.checkVersion(request.version());

        String assigneeName = requireActiveMemberName(ctx, request.assigneeMemberId());
        deal.changeAssignee(request.assigneeMemberId());

        return DealResponses.DealItem.of(deal,
                customerQuery.get(ctx, deal.getCustomerId()).name(), assigneeName);
    }

    /** 다음 단계 (DL-07) — 인접만. 협상에서 호출하면 DEAL_WON_REQUIRES_ORDER */
    @Transactional
    public DealResponses.DealItem advance(AccessContext ctx, UUID dealId, DealRequests.StageMove request) {
        return moveStage(ctx, dealId, request.version(), Deal::advance);
    }

    /** 이전 단계 (DL-08) — 리드에서는 되돌릴 곳이 없다 */
    @Transactional
    public DealResponses.DealItem revert(AccessContext ctx, UUID dealId, DealRequests.StageMove request) {
        return moveStage(ctx, dealId, request.version(), Deal::revert);
    }

    /**
     * 실패 처리 (DL-10·11).
     *
     * <p><b>진행 중 견적·열람 링크 만료(전이표 §5의 효과)는 아직 붙지 않았다</b> —
     * quote가 Modulith상 다른 모듈이라 조회 창구가 없다. 이슈 #61 「리뷰 필요」 1번이
     * 정해지면 이 메서드 안에서 같은 트랜잭션으로 호출한다.
     */
    @Transactional
    public DealResponses.DealItem lose(AccessContext ctx, UUID dealId, DealRequests.LoseDeal request) {
        return moveStage(ctx, dealId, request.version(), deal -> deal.lose(request.reason()));
    }

    /** 재개 (DL-12) — 실패 직전 단계로. 만료된 견적·링크는 복원하지 않는다 */
    @Transactional
    public DealResponses.DealItem reopen(AccessContext ctx, UUID dealId, DealRequests.StageMove request) {
        return moveStage(ctx, dealId, request.version(), Deal::reopen);
    }

    /**
     * 전이 4종의 공통 뼈대 — 범위 판정 → 낙관적 락 → 전이 → 응답 조립.
     *
     * <p>전이 규칙 자체는 엔티티가 판정한다 (전이표 §5). 여기서 단계를 비교하지 않는 이유는,
     * 서비스에 조건을 흩으면 호출 경로가 늘 때마다 규칙이 새어 나가기 때문이다.
     */
    private DealResponses.DealItem moveStage(AccessContext ctx, UUID dealId, Integer version,
                                             java.util.function.Consumer<Deal> transition) {
        Deal deal = findInScope(ctx, dealId);
        deal.checkVersion(version);

        // 전이 전 단계를 먼저 잡는다 — 바꾼 뒤에는 before를 알 방법이 없다 (#22 changes 규약)
        Deal.Stage before = deal.getStage();
        transition.accept(deal);
        publishStageChanged(deal, before, AuditActor.member(ctx.memberId()));

        return DealResponses.DealItem.of(deal,
                customerQuery.get(ctx, deal.getCustomerId()).name(),
                memberQuery.get(deal.getAssigneeMemberId()).name());
    }

    /**
     * 고객사 id 묶음 → 이름 (SC-01). <b>지워진 고객사는 결과에서 빠져 그 줄만 이름이 null이 된다</b> —
     * 목록 한 줄 때문에 전체가 404가 되지 않게 하는 것이 {@code namesByIds}의 계약이다 (B javadoc).
     * 단건 경로({@code create}·{@code update} 등)는 그대로 {@code get}이다 — 거기서는 없으면 404가 맞다.
     */
    private Map<UUID, String> customerNamesOf(UUID companyId, Collection<UUID> customerIds) {
        return customerQuery.namesByIds(companyId, customerIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(CustomerQuery.CustomerSummary::id,
                        CustomerQuery.CustomerSummary::name));
    }

    /**
     * 단계 전이 감사 이벤트 (AC-07, #22) — <b>실제로 바뀐 경우에만</b> 발행한다.
     *
     * <p>수동 전이는 규칙상 항상 바뀌지만(안 바뀌면 엔티티가 던진다), 판정을 호출부에 흩지 않고
     * 여기 한 곳에 둔다 — 자동 승급·성사(멱등)와 같은 규칙을 쓰기 위해서다.
     *
     * <p>{@code lostReason}은 실패 전이일 때만 실린다 (DL-11) — 그 외 단계에서는 엔티티 값이
     * 이미 null이라 그대로 넘겨도 규약(#22 3번)과 어긋나지 않는다.
     */
    private void publishStageChanged(Deal deal, Deal.Stage before, AuditActor actor) {
        if (deal.getStage() == before) {
            return;
        }
        eventPublisher.publishEvent(new DealStageChanged(
                deal.getCompanyId(), deal.getId(), actor, Instant.now(),
                before.name(), deal.getStage().name(), deal.getLostReason()));
    }

    /**
     * 회사 스코프 + 미삭제 조회. 범위 밖이면 존재 여부를 구별하지 않고 404다 (SC-09).
     *
     * <p>영업이 남의 Deal을 지정한 경우도 여기서 404가 아니라, 아래 담당 판정에서 걸린다 —
     * 회사 안에는 존재하기 때문이다. 응답 문구는 동일하다.
     */
    private Deal findInScope(AccessContext ctx, UUID dealId) {
        Deal deal = dealRepository.findByIdAndCompanyIdAndDeletedAtIsNull(dealId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (ctx.scope() == AccessScope.OWNED_ONLY && !deal.getAssigneeMemberId().equals(ctx.memberId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);   // SC-02 — 권한 아닌 존재로 답한다
        }
        return deal;
    }

    /**
     * 배정 대상 검증 — 같은 회사의 활성 구성원이어야 한다 (DL-04·05, SC-06).
     *
     * <p>{@code MemberQuery.isActive}는 활성 여부만 답하고 소속 회사를 알려주지 않는다.
     * 그래서 회사의 활성 구성원 목록에서 찾는 방식으로 <b>소속과 활성을 한 번에</b> 판정한다
     * (구성원 5~30명 전제, 01 §2.1). 실패는 404다 (검증 노트 #3).
     */
    private String requireActiveMemberName(AccessContext ctx, UUID memberId) {
        return memberQuery.findAllActive(ctx.companyId()).stream()
                .filter(member -> member.id().equals(memberId))
                .findFirst()
                .map(MemberQuery.MemberSummary::name)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
