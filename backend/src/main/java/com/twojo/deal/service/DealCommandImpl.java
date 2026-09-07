package com.twojo.deal.service;

import com.twojo.boundary.DealCommand;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DealCommand} 구현 — 시스템 전이의 유일한 통로다.
 *
 * <p><b>회사 스코프를 걸지 않는다.</b> 호출자(견적 발송)가 이미 회사 안에서 얻은 dealId를
 * 넘기는 자리이고, 그 경로에서 SC-01·02 판정이 끝나 있다 — {@code assigneeIdOf}·{@code isOpen}과
 * 같은 규약이다 (docs/11 §7.2). <b>구성원 요청을 직접 받는 경로에서는 쓰지 않는다.</b>
 *
 * <p>{@code REQUIRES_NEW}를 붙이지 않는다 — 발송 트랜잭션이 롤백되면 단계 승급도 함께
 * 되돌아가야 한다. 별도 커밋되면 "발송은 실패했는데 딜만 견적 단계로 올라간" 상태가 남는다.
 * D의 {@code ViewTokenCommandImpl}이 같은 이유로 합류한다.
 */
@Service
@RequiredArgsConstructor
class DealCommandImpl implements DealCommand {

    private final DealRepository dealRepository;

    @Override
    @Transactional
    public void promoteToQuoteStage(UUID dealId) {
        Deal deal = dealRepository.findByIdAndDeletedAtIsNull(dealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        deal.promoteToQuoteStage();   // 종결이면 여기서 막힌다 · 견적·협상이면 무동작
    }
}
