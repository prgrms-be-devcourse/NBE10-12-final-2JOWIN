package com.twojo.activity.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.DealQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 활동 이력의 범위 판정 — 상담 기록도 할 일도 <b>Deal이 보이면 보인다</b> (09 §59·61).
 *
 * <p>둘 다 자기 담당 축이 없어 판정이 같다. 서비스마다 복사해 두면 한쪽만 고쳐지는 날이 온다.
 * 다른 모듈(견적·주문)이 각자 같은 판정을 갖는 것은 모듈 경계 때문이고, 여기는 한 패키지다.
 */
@Component
@RequiredArgsConstructor
class DealAccess {

    private final DealQuery dealQuery;

    /**
     * 이 Deal이 요청의 범위 안에 있는지 — 회사가 다르거나 담당이 아니면 404 (SC-01·02·09).
     * 존재 여부를 구별하지 않는다.
     */
    void requireInScope(AccessContext ctx, UUID dealId) {
        if (dealQuery.summariesByIds(ctx.companyId(), List.of(dealId)).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (ctx.scope() == AccessScope.OWNED_ONLY
                && !ctx.memberId().equals(dealQuery.assigneeIdOf(dealId))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    /** 담당 Deal id 전체 — {@code OWNED_ONLY}일 때만 부른다 (계약 javadoc). */
    List<UUID> assignedDealIds(AccessContext ctx) {
        return dealQuery.assignedDealIds(ctx.companyId(), ctx.memberId());
    }

    /** 그 고객사의 Deal id 전체 — 회사 스코프는 호출자가 고객사 쪽에서 이미 확인한다. */
    List<UUID> dealIdsOfCustomer(UUID customerId) {
        return dealQuery.summariesByCustomer(customerId).stream()
                .map(DealQuery.DealSummary::id)
                .toList();
    }
}
