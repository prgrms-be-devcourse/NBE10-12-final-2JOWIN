package com.twojo.deal.service;

import com.twojo.boundary.DealQuery;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link DealQuery} 스텁 — 빈 자리만 채운다.
 *
 * <p>B의 고객사 삭제 판정(CU-08)·상세 이력(CU-12)·활동 집계 범위(SC-02)와
 * D의 알림 수신자 결정(Q-26)이 이 빈을 주입받는다.
 * 실제 구현은 Deal 이슈에서 이 클래스의 {@code throw}를 대체한다.
 *
 * <p><b>조회 스텁은 예외를 던진다</b> — {@code hasOpenDeals}가 false를 돌려주면
 * 진행 중 Deal이 있는 고객사가 삭제되고, {@code assignedDealIds}가 빈 목록을 돌려주면
 * SC-02 필터가 전부 빈 결과가 된다. 둘 다 조용히 틀리는 쪽이라 막는다.
 */
@Service
public class DealQueryImpl implements DealQuery {

    @Override
    public UUID assigneeIdOf(UUID dealId) {
        throw new UnsupportedOperationException("DealQuery.assigneeIdOf — C 2주차 구현 예정");
    }

    @Override
    public boolean isOpen(UUID dealId) {
        throw new UnsupportedOperationException("DealQuery.isOpen — C 2주차 구현 예정");
    }

    @Override
    public boolean hasOpenDeals(UUID customerId) {
        throw new UnsupportedOperationException("DealQuery.hasOpenDeals — C 2주차 구현 예정");
    }

    @Override
    public List<DealSummary> summariesByCustomer(UUID customerId) {
        throw new UnsupportedOperationException("DealQuery.summariesByCustomer — C 2주차 구현 예정");
    }

    @Override
    public List<DealSummary> summariesByIds(UUID companyId, Collection<UUID> dealIds) {
        throw new UnsupportedOperationException("DealQuery.summariesByIds — C 2주차 구현 예정");
    }

    @Override
    public List<UUID> assignedDealIds(UUID companyId, UUID memberId) {
        throw new UnsupportedOperationException("DealQuery.assignedDealIds — C 2주차 구현 예정");
    }
}
