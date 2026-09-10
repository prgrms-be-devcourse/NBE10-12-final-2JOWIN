package com.twojo.order.service;

import com.twojo.boundary.OrderQuery;
import com.twojo.order.entity.Order;
import com.twojo.order.repository.OrderRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderQuery} 구현 — 계약의 이유는 인터페이스 javadoc에 있다.
 *
 * <p><b>범위를 다시 판정하지 않는다.</b> 넘어온 {@code quoteIds}를 그대로 조건에 건다 —
 * 판정은 축(deal.assignee_member_id)을 가진 호출자 몫이다 (SC-04).
 * {@code QuoteCommandImpl}의 고객 경로 3종이 회사 스코프를 걸지 않는 것과 같은 구조다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class OrderQueryImpl implements OrderQuery {

    private final OrderRepository orderRepository;

    @Override
    public List<QuoteWonTotal> wonTotalsByQuotes(UUID companyId, Collection<UUID> quoteIds,
                                                 LocalDate from, LocalDate to) {
        return orderRepository
                .findConverted(companyId, OrderPeriod.startOfDay(from), OrderPeriod.startOfNextDay(to), quoteIds)
                .stream()
                .map(order -> new QuoteWonTotal(order.getQuoteId(), order.getTotalAmount()))
                .toList();
    }

    /**
     * 딜 상세의 주문 목록 (DL-15).
     *
     * <p><b>빈 묶음이면 조회하지 않는다</b> — 여기서는 null을 "제한 없음"으로 읽지 않는다
     * ({@code wonTotalsByQuotes}와 갈리는 지점, 계약 javadoc). 딜 상세는 언제나 특정 딜의
     * 견적으로 좁혀진 자리라, 빈 묶음에 회사 전체 주문이 붙으면 그대로 사고다.
     */
    @Override
    public List<OrderBrief> briefsByQuotes(UUID companyId, Collection<UUID> quoteIds) {
        if (quoteIds == null || quoteIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findByCompanyIdAndQuoteIdInOrderByCreatedAtDesc(companyId, quoteIds).stream()
                .map(order -> new OrderBrief(order.getId(), order.getQuoteId(), order.getOrderNo(),
                        order.getTotalAmount(), order.getCreatedAt()))
                .toList();
    }
}
