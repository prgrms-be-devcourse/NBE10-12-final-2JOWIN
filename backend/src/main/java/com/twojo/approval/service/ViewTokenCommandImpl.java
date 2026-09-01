package com.twojo.approval.service;

import com.twojo.approval.entity.QuoteViewToken;
import com.twojo.approval.repository.QuoteViewTokenRepository;
import com.twojo.boundary.ViewTokenCommand;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ViewTokenCommand} 구현.
 *
 * <p>{@code expire()} — 실구현. 견적의 ACTIVE 링크 1건을 EXPIRED + 사유로 전이한다.
 * <b>멱등</b>: ACTIVE 링크가 없으면(이미 만료·응답 완료·미발급) 예외 없이 무동작한다 —
 * C의 회수·Deal 실패·만료 배치가 경쟁적으로 불러도 안전하다(최초 사유 보존).
 *
 * <p>{@code issue()} — 미구현. {@code throw}로 표식한다. no-op으로 두면 토큰 없는 SENT
 * 견적이 만들어져 D의 열람·승인이 찾을 토큰이 없어진다(Q-40 불변식). 2주차 견적 이슈에서
 * {@code QuoteQuery.getPublicView} 확정 후 구현한다.
 *
 * <p>{@code issue}/{@code expire}는 <b>호출자가 연 트랜잭션에 합류</b>한다 —
 * {@code issue}는 C의 발송 트랜잭션(Q-40), {@code expire}는 C의 회수·Deal 실패·만료 배치
 * 트랜잭션. {@code @Transactional(REQUIRES_NEW)}를 붙이지 않는다 — 토큰만 별도 커밋되면
 * 호출자 롤백 시 링크가 살아남는다(고아 링크).
 *
 * <p>계약 {@code ViewTokenCommand.ExpiredReason} ↔ 엔티티 {@code QuoteViewToken.ExpiredReason}은
 * 별도 enum이다(엔티티는 boundary 무의존). 값 이름이 1:1이라 {@code valueOf(name())}으로 잇는다.
 */
@Service
@RequiredArgsConstructor
class ViewTokenCommandImpl implements ViewTokenCommand {

    private final QuoteViewTokenRepository quoteViewTokenRepository;

    @Override
    public void issue(UUID quoteId, UUID recipientContactId) {
        throw new UnsupportedOperationException("ViewTokenCommand.issue — D 2주차 구현 예정");
    }

    @Override
    @Transactional
    public void expire(UUID quoteId, ExpiredReason reason) {
        // ACTIVE 링크가 없으면(이미 만료·응답 완료·미발급) 무동작 — 멱등 계약: 경쟁 호출·중복 호출 안전.
        quoteViewTokenRepository.findActiveByQuoteId(quoteId)
                .ifPresent(token -> token.expire(QuoteViewToken.ExpiredReason.valueOf(reason.name())));
    }
}
