package com.twojo.quote.service;

import com.twojo.boundary.QuoteCommand;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link QuoteCommand} 스텁 — 빈 자리만 채운다.
 *
 * <p>D의 열람·승인 API가 이 빈을 주입받아야 컨텍스트가 뜬다. 실제 구현은 견적 상태 전이
 * 이슈에서 이 클래스의 {@code throw}를 대체한다 (docs/11-work-breakdown.md §4·§7.1).
 *
 * <p><b>세 메서드 모두 예외를 던진다.</b> 조용히 성공하면 견적 상태가 바뀌지 않은 채
 * D의 토큰만 RESPONDED로 소진되어, 고객이 승인했는데 견적은 SENT인 상태가 남는다.
 * 크게 터지는 편이 안전하다 (D가 {@code ViewTokenCommand.issue}를 throw로 둔 것과 같은 판단).
 *
 * <p>승인·반려 트랜잭션은 D가 열고 이 구현이 참여한다 — 실구현에도
 * {@code @Transactional(REQUIRES_NEW)}를 붙이지 않는다 (경계 합의, 11 §5).
 */
@Service
public class QuoteCommandImpl implements QuoteCommand {

    @Override
    public void markViewed(UUID quoteId) {
        throw new UnsupportedOperationException("QuoteCommand.markViewed — C 2주차 구현 예정");
    }

    @Override
    public void approve(UUID quoteId, Responder responder) {
        throw new UnsupportedOperationException("QuoteCommand.approve — C 2주차 구현 예정");
    }

    @Override
    public void reject(UUID quoteId, String reason, Responder responder) {
        throw new UnsupportedOperationException("QuoteCommand.reject — C 2주차 구현 예정");
    }
}
