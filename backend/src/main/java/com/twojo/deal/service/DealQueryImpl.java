package com.twojo.deal.service;

import com.twojo.boundary.DealQuery;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class DealQueryImpl implements DealQuery {

    private final DealRepository dealRepository;

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

    /** CU-08 — 진행 중 Deal이 하나라도 있으면 고객사를 삭제할 수 없다 */
    @Override
    public boolean hasOpenDeals(UUID customerId) {
        return dealRepository.existsByCustomerIdAndStageInAndDeletedAtIsNull(customerId, Deal.OPEN_STAGES);
    }

    /** CU-12 — 고객사 상세의 Deal 이력. 최신순, 종결 Deal 포함 */
    @Override
    public List<DealSummary> summariesByCustomer(UUID customerId) {
        return dealRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId).stream()
                .map(DealQueryImpl::toSummary)
                .toList();
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
        return dealRepository.findByCompanyIdAndIdInAndDeletedAtIsNull(companyId, dealIds).stream()
                .map(DealQueryImpl::toSummary)
                .toList();
    }

    /** SC-02 범위 필터 — 담당 Deal id 전체. 종결도 포함하고 소프트 삭제만 제외한다 */
    @Override
    public List<UUID> assignedDealIds(UUID companyId, UUID memberId) {
        return dealRepository.findIdsByAssignee(companyId, memberId);
    }

    /**
     * {@code wonAmount}는 주문 합계(DL-18)라 orders 조회가 필요하다.
     * <b>주문 전환 이슈까지 null이다</b> — 소비자(B·D)는 성사 금액을 이 창구로 받지 않는다.
     */
    private static DealSummary toSummary(Deal deal) {
        return new DealSummary(deal.getId(), deal.getTitle(), deal.getStage().name(),
                deal.getExpectedAmount(), null, deal.getCreatedAt());
    }
}
