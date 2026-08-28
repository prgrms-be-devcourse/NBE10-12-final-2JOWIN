package com.twojo.approval.service;

import com.twojo.boundary.ViewTokenCommand;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link ViewTokenCommand} 스텁 — 빈 자리만 채운다 (이슈 #14).
 *
 * <p>C의 2주차 견적 발송이 {@code viewTokenCommand.issue(...)}를 주입받아 쓰려면
 * 이 인터페이스의 구현 빈이 존재해야 한다 — 없으면 컨텍스트 로딩이 실패한다.
 * 그래서 로직 없이 빈만 먼저 등록한다 (docs/11-work-breakdown.md §5·§7.2, C 확인 요청서 §3.4).
 *
 * <p>실제 구현(토큰 발급·해시 저장·email_log SCHEDULED 예약, 멱등 만료)은
 * 2주차 열람 링크 이슈에서 이 클래스의 {@code throw}를 대체한다.
 *
 * <p>승인·반려 트랜잭션은 D가 열고 C의 {@code QuoteCommand}가 참여하는 구조이므로
 * (경계 합의), 실구현에도 {@code @Transactional(REQUIRES_NEW)}를 붙이지 않는다.
 */
@Service
public class ViewTokenCommandImpl implements ViewTokenCommand {

    @Override
    public void issue(UUID quoteId, UUID recipientContactId) {
        throw new UnsupportedOperationException("ViewTokenCommand.issue — D 2주차 구현 예정");
    }

    @Override
    public void expire(UUID quoteId, ExpiredReason reason) {
        throw new UnsupportedOperationException("ViewTokenCommand.expire — D 2주차 구현 예정");
    }
}
