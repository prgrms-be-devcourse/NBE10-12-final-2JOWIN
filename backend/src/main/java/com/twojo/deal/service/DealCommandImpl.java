package com.twojo.deal.service;

import com.twojo.boundary.AuditActor;
import com.twojo.boundary.DealCommand;
import com.twojo.deal.DealStageChanged;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DealCommand} 구현 — 시스템 전이의 유일한 통로다.
 *
 * <p><b>승급은 회사 스코프를 걸지 않는다.</b> 호출자(견적 발송)가 이미 회사 안에서 얻은 dealId를
 * 넘기는 자리이고, 그 경로에서 SC-01·02 판정이 끝나 있다 — {@code assigneeIdOf}·{@code isOpen}과
 * 같은 규약이다 (docs/11 §7.2). <b>구성원 요청을 직접 받는 경로에서는 쓰지 않는다.</b>
 * 이관은 계약이 {@code companyId}를 명시로 받으므로 조회에 그대로 건다.
 *
 * <p>{@code REQUIRES_NEW}를 붙이지 않는다 — 발송(또는 비활성화) 트랜잭션이 롤백되면 단계 승급(또는 이관)도
 * 함께 되돌아가야 한다. 별도 커밋되면 "발송은 실패했는데 딜만 견적 단계로 올라간" 상태가 남는다.
 * D의 {@code ViewTokenCommandImpl}이 같은 이유로 합류한다.
 */
@Service
@RequiredArgsConstructor
class DealCommandImpl implements DealCommand {

    private final DealRepository dealRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void promoteToQuoteStage(UUID dealId) {
        Deal deal = find(dealId);
        Deal.Stage before = deal.getStage();
        deal.promoteToQuoteStage();   // 종결이면 여기서 막힌다 · 견적·협상이면 무동작
        publishIfMoved(deal, before);
    }

    @Override
    @Transactional
    public void markWon(UUID dealId) {
        Deal deal = find(dealId);
        Deal.Stage before = deal.getStage();
        deal.win();   // 이미 성사면 무동작 · 실패면 DEAL_NOT_OPEN
        publishIfMoved(deal, before);
    }

    /**
     * 시스템 전이 감사 이벤트 (AC-07, #22) — <b>단계가 실제로 바뀐 경우에만</b> 발행한다.
     *
     * <p>이 계약의 두 메서드는 <b>멱등</b>이다. 이미 견적·협상인 딜의 승급(Q-25)과 이미 성사인
     * 딜의 성사(OD-06)는 아무 일도 하지 않는데, 그때도 발행하면 타임라인에 "견적 → 견적" 같은
     * <b>일어나지 않은 변화</b>가 쌓인다. 두 번째 승인 견적을 전환할 때마다 성사 기록이 늘어난다.
     *
     * <p>행위자는 {@code SYSTEM}이다 — 다른 도메인의 사건이 Deal을 움직인 것이라 사람이 없다 (#22 3번).
     * {@code lostReason}은 이 두 전이의 도착 단계가 LOST가 아니라 항상 null이다.
     */
    private void publishIfMoved(Deal deal, Deal.Stage before) {
        if (deal.getStage() == before) {
            return;
        }
        eventPublisher.publishEvent(new DealStageChanged(
                deal.getCompanyId(), deal.getId(), AuditActor.system(), Instant.now(),
                before.name(), deal.getStage().name(), null));
    }

    /** 없으면 RESOURCE_NOT_FOUND — 조용히 무동작하면 주문은 생겼는데 딜은 그대로인 상태가 남는다 */
    private Deal find(UUID dealId) {
        return dealRepository.findByIdAndDeletedAtIsNull(dealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 진행 중 담당 Deal 이관 (MB-14) — <b>엔티티를 경유</b>한다.
     *
     * <p>조회 조건은 {@code DealQuery.countOpenAssigned}와 같은 파생 쿼리라 사전 판정 건수와 반환 건수가 맞는다.
     * 한 건씩 {@link Deal#changeAssignee}로 바꾸면 flush 때 {@code @Version}이 올라가 열어 둔 딜 상세의
     * 낙관적 락(DL-05)이 이관을 알아챈다 — JPQL 일괄 update로는 그게 안 된다. 구성원당 몇 건이라 성능은 문제가 아니다.
     *
     * <p>{@code toMemberId}가 같은 회사의 활성 구성원인지는 계약대로 호출자(A)가 먼저 봤다고 믿는다.
     */
    @Override
    @Transactional
    public List<UUID> reassignOpenDeals(UUID companyId, UUID fromMemberId, UUID toMemberId) {
        List<Deal> deals = dealRepository.findByCompanyIdAndAssigneeMemberIdAndStageInAndDeletedAtIsNull(
                companyId, fromMemberId, Deal.OPEN_STAGES);
        deals.forEach(deal -> deal.changeAssignee(toMemberId));
        return deals.stream().map(Deal::getId).toList();
    }
}
