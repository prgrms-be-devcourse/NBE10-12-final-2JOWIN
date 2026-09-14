package com.twojo.deal.service;

import com.twojo.boundary.DealQuery;
import com.twojo.boundary.OrderQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DealQuery} 구현 — 타 도메인이 Deal 정보를 얻는 유일한 통로다 (docs/11 §7.2).
 *
 * <p>B의 고객사 삭제 판정(CU-08)·상세 이력(CU-12)·활동 집계 범위(SC-02)와
 * D의 알림 수신자 결정(Q-26)·열람 페이지 현재 담당자(AP-18)가 여기를 지난다.
 *
 * <p><b>소프트 삭제된 Deal은 어디에서도 보이지 않는다</b> (§1.5).
 * 다만 종결(WON·LOST) Deal은 포함한다 — 최근 활동(DB-04)에는 성사된 딜의 이력도 나와야 한다.
 *
 * <p>{@code assigneeIdOf}·{@code isOpen}은 계약상 companyId를 받지 않는다 —
 * 호출자가 이미 회사 안에서 얻은 dealId를 넘기는 자리이기 때문이다.
 * 구성원 요청을 직접 받는 경로에서는 회사 스코프가 걸린 조회를 쓴다 (SC-01).
 *
 * <p><b>{@link QuoteQuery}는 {@link ObjectProvider}로 받는다.</b> {@code QuoteQueryImpl}이 딜 제목을
 * 채우려고 이 계약({@link DealQuery})을 주입받아, 생성자 주입끼리 서로를 기다리는 순환이 된다 —
 * Spring Boot는 순환 참조를 기본으로 막아 기동이 실패한다. 호출 시점에 꺼내면 생성 순서가 끊긴다.
 * {@link OrderQuery}는 이 계약에 기대지 않아 그대로 받는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class DealQueryImpl implements DealQuery {

    private final DealRepository dealRepository;
    private final ObjectProvider<QuoteQuery> quoteQuery;
    private final OrderQuery orderQuery;

    /** 없으면 RESOURCE_NOT_FOUND — 알림 수신자·담당자 표시가 걸린 자리라 조용히 null을 돌려주지 않는다 */
    @Override
    public UUID assigneeIdOf(UUID dealId) {
        return dealRepository.findByIdAndDeletedAtIsNull(dealId)
                .map(Deal::getAssigneeMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 진행 중(리드~협상) 여부. <b>없거나 삭제된 Deal은 false</b>다 —
     * 이 판정의 소비자는 "여기에 견적을 더 붙여도 되는가"를 묻고, 없는 Deal의 답은 "안 된다"이다.
     */
    @Override
    public boolean isOpen(UUID dealId) {
        return dealRepository.findByIdAndDeletedAtIsNull(dealId)
                .map(Deal::isOpen)
                .orElse(false);
    }

    /** 없으면 RESOURCE_NOT_FOUND — 수신인 검증의 기준값이라 없으면 호출자가 진행할 수 없다 */
    @Override
    public UUID customerIdOf(UUID dealId) {
        return dealRepository.findByIdAndDeletedAtIsNull(dealId)
                .map(Deal::getCustomerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** CU-08 — 진행 중 Deal이 하나라도 있으면 고객사를 삭제할 수 없다 */
    @Override
    public boolean hasOpenDeals(UUID customerId) {
        return dealRepository.existsByCustomerIdAndStageInAndDeletedAtIsNull(customerId, Deal.OPEN_STAGES);
    }

    /** CU-12 — 고객사 상세의 Deal 이력. 최신순, 종결 Deal 포함. 성사 딜에는 주문 합계를 싣는다 (DL-18) */
    @Override
    public List<DealSummary> summariesByCustomer(UUID customerId) {
        List<Deal> deals = dealRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId);
        if (deals.isEmpty()) {
            return List.of();
        }
        // 고객사는 한 회사에만 속하므로 그 딜들의 회사가 곧 주문 조회의 스코프다 (SC-01)
        return toSummaries(deals.get(0).getCompanyId(), deals);
    }

    /**
     * DB-04·05 — 활동·할 일 목록에 붙일 딜 제목 배치 조회.
     * 빈 목록을 넘기면 조회하지 않고 빈 목록을 돌려준다. 없는 id는 결과에서 빠진다.
     */
    @Override
    public List<DealSummary> summariesByIds(UUID companyId, Collection<UUID> dealIds) {
        if (dealIds == null || dealIds.isEmpty()) {
            return List.of();
        }
        return toSummaries(companyId, dealRepository.findByCompanyIdAndIdInAndDeletedAtIsNull(companyId, dealIds));
    }

    /** SC-02 범위 필터 — 담당 Deal id 전체. 종결도 포함하고 소프트 삭제만 제외한다 */
    @Override
    public List<UUID> assignedDealIds(UUID companyId, UUID memberId) {
        return dealRepository.findIdsByAssignee(companyId, memberId);
    }

    /** MB-14 — 진행 중 담당 Deal 건수. 종결·소프트 삭제 제외. 이관(reassignOpenDeals)이 옮기는 집합과 같다 */
    @Override
    public long countOpenAssigned(UUID companyId, UUID memberId) {
        return dealRepository.countByCompanyIdAndAssigneeMemberIdAndStageInAndDeletedAtIsNull(
                companyId, memberId, Deal.OPEN_STAGES);
    }

    private List<DealSummary> toSummaries(UUID companyId, List<Deal> deals) {
        Map<UUID, Long> wonAmounts = wonAmountsOf(companyId, deals);
        return deals.stream()
                .map(deal -> toSummary(deal, wonAmounts.get(deal.getId())))
                .toList();
    }

    /**
     * 성사 딜의 주문 합계 (DL-18) — <b>묶음 전체를 두 번의 조회로</b> 얻는다.
     * 딜 목록의 {@code DealService.wonAmountsOf}와 같은 규칙이다.
     *
     * <p><b>성사(WON) 딜만 묻는다.</b> 성사는 주문 전환만이 만들어(DL-09) 진행 중인 딜에는 주문이 없고,
     * 성사 딜이 하나도 없으면 두 창구 모두 부르지 않는다 — 진행 중 딜만 묻는 호출은 비용이 0이다.
     * 주문 목록처럼 성사 딜이 섞이는 묶음은 조회가 두 번 늘어난다 (#358).
     *
     * <p>주문에는 {@code deal_id}가 없어 <b>견적을 한 홉 지나</b> 딜로 되짚는다.
     */
    private Map<UUID, Long> wonAmountsOf(UUID companyId, List<Deal> deals) {
        List<UUID> wonDealIds = deals.stream()
                .filter(deal -> deal.getStage() == Deal.Stage.WON)
                .map(Deal::getId)
                .toList();
        if (wonDealIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UUID> dealByQuote = quoteQuery.getObject().briefsByDeals(companyId, wonDealIds).stream()
                .collect(Collectors.toMap(QuoteQuery.QuoteBrief::id, QuoteQuery.QuoteBrief::dealId));

        return orderQuery.briefsByQuotes(companyId, dealByQuote.keySet()).stream()
                .collect(Collectors.groupingBy(
                        order -> dealByQuote.get(order.quoteId()),
                        Collectors.summingLong(OrderQuery.OrderBrief::totalAmount)));
    }

    /** 성사 전에는 {@code wonAmount}가 null이다 — 0을 넣으면 화면이 "주문 0원"으로 읽는다 (08 표시 규칙) */
    private static DealSummary toSummary(Deal deal, Long wonAmount) {
        return new DealSummary(deal.getId(), deal.getCustomerId(), deal.getTitle(), deal.getStage().name(),
                deal.getExpectedAmount(), wonAmount, deal.getCreatedAt());
    }
}
