package com.twojo.quote.service;

import com.twojo.boundary.ViewTokenCommand;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.repository.QuoteRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기간 만료 견적 1건을 닫는다 (Q-37). {@link QuoteExpiryBatch}가 견적마다 호출한다.
 *
 * <p><b>{@code @Transactional(REQUIRES_NEW)}</b> — {@code @Scheduled} 메서드엔 트랜잭션이 없다.
 * 견적 단위로 여는 이유는 {@link com.twojo.notification.service.RemindWorker}와 같다:
 * 한 견적의 데이터 이상이 나머지를 롤백시키면 안 되고, <b>견적 상태와 링크 상태는
 * 원자적이어야</b> 하기 때문이다 — 견적만 EXPIRED가 되고 링크가 살아 있으면 고객이
 * 만료된 견적을 계속 열람한다.
 *
 * <p><b>순서는 견적 → 링크다.</b> {@code ViewTokenCommand.expire}는 REQUIRES_NEW를 붙이지
 * 않아 이 트랜잭션에 합류하므로(그쪽 javadoc), 어느 쪽이 실패해도 둘 다 롤백된다.
 * 견적을 먼저 닫는 것은 그쪽이 이 모듈의 소유라 실패 원인이 단순하기 때문이다.
 *
 * <p><b>이미 닫힌 견적은 링크를 건드리지 않는다.</b> {@code Quote.expire()}가 {@code false}면
 * 배치 조회 이후 누군가 회수·승인·반려했다는 뜻이고, 그 전이들은 각자 링크를 이미 처리했다
 * (회수는 {@code WITHDRAWN}, 승인·반려는 {@code RESPONDED}). 거기에 {@code TIME}을 덮어쓰면
 * 만료 사유가 거짓이 된다.
 */
@Component
@RequiredArgsConstructor
class QuoteExpiryWorker {

    private final QuoteRepository quoteRepository;
    private final ViewTokenCommand viewTokenCommand;

    /**
     * @return 이 호출로 실제 만료 전이가 일어났으면 {@code true} — 배치가 건수를 집계한다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(UUID quoteId) {
        Quote quote = quoteRepository.findById(quoteId).orElse(null);
        if (quote == null || !quote.expire()) {
            return false;   // 조회와 이 트랜잭션 사이에 상태가 바뀌었다 — 배치라 오류가 아니다
        }
        viewTokenCommand.expire(quoteId, ViewTokenCommand.ExpiredReason.TIME);
        return true;
    }
}
