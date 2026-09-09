package com.twojo.approval.service;

import com.twojo.approval.entity.QuoteViewToken;
import com.twojo.approval.repository.QuoteViewTokenRepository;
import com.twojo.boundary.ViewTokenQuery;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ViewTokenQuery} 구현 — B의 CU-14(발송 이력 있는 담당자 삭제 차단) 판정용,
 * notification 모듈의 NT-12 실패 알림 경로용.
 *
 * <p>{@code quote_view_token}은 D(approval) 소유라 다른 모듈이 직접 조회할 수 없어 이 경계로 답한다
 * (docs/11-work-breakdown.md §7.2). 삭제 차단({@code CONTACT_HAS_QUOTES}) 판정과 NT-12 수신자 해석은
 * 호출자에 있고, 여기서는 토큰 행의 사실만 리포지토리에 위임한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ViewTokenQueryImpl implements ViewTokenQuery {

    private final QuoteViewTokenRepository quoteViewTokenRepository;

    @Override
    public boolean existsForContact(UUID contactId) {
        return quoteViewTokenRepository.existsByRecipientContactId(contactId);
    }

    @Override
    public Optional<UUID> quoteIdOf(UUID tokenId) {
        return quoteViewTokenRepository.findById(tokenId).map(QuoteViewToken::getQuoteId);
    }
}
