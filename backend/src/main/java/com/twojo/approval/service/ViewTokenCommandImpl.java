package com.twojo.approval.service;

import com.twojo.boundary.ViewTokenCommand;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link ViewTokenCommand} 스텁 — 빈 자리만 채운다 (이슈 #14).
 *
 * <p>C의 2주차 견적 발송·회수가 {@code viewTokenCommand}를 주입받아 쓰려면 이 인터페이스의
 * 구현 빈이 존재해야 한다 — 없으면 컨텍스트 로딩이 실패한다. 그래서 로직 없이 빈만 먼저
 * 등록한다 (docs/11-work-breakdown.md §5·§7.2, C 확인 요청서 §3.4).
 *
 * <p>2주차 열람 링크 이슈에서 실제 로직으로 대체한다.
 * <ul>
 *   <li>{@code expire()} — 스텁 단계부터 <b>no-op</b>. 멱등 계약(이미 만료·활성 링크 없음 →
 *       예외 없이 무동작)을 미리 적용해, C의 회수(QT-17, 2주차) 흐름을 막지 않는다.</li>
 *   <li>{@code issue()} — 미구현 표식으로 {@code throw}. no-op으로 두면 토큰 없는 SENT
 *       견적이 만들어져 D의 열람·승인이 찾을 토큰이 없어진다(Q-40 불변식).</li>
 * </ul>
 *
 * <p>{@code issue}/{@code expire}는 <b>호출자가 연 트랜잭션에 합류</b>한다 —
 * {@code issue}는 C의 발송 트랜잭션(Q-40), {@code expire}는 C의 회수·Deal 실패·만료 배치
 * 트랜잭션. 실구현에도 {@code @Transactional(REQUIRES_NEW)}를 붙이지 않는다 — 토큰만 별도
 * 커밋되면 호출자 롤백 시 링크가 살아남는다(고아 링크).
 */
@Service
public class ViewTokenCommandImpl implements ViewTokenCommand {

    @Override
    public void issue(UUID quoteId, UUID recipientContactId) {
        throw new UnsupportedOperationException("ViewTokenCommand.issue — D 2주차 구현 예정");
    }

    @Override
    public void expire(UUID quoteId, ExpiredReason reason) {
        // 스텁 no-op — 2주차 실구현 전까지 무동작.
        // expire()의 정식 계약이 멱등 no-op이라 C의 회수(QT-17) 흐름을 막지 않는다.
    }
}
