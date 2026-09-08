package com.twojo.approval.service;

import com.twojo.boundary.NotificationCommand;
import com.twojo.boundary.QuoteCommand;
import com.twojo.boundary.QuoteQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET 열람의 "첫 열람" 부수효과 — SENT → VIEWED + NT-03(QUOTE_VIEWED). <b>조회와 분리된 자체
 * read-write 트랜잭션</b>이다.
 *
 * <p><b>왜 별 트랜잭션인가</b> — GET 경로엔 이것과 원자적으로 묶여야 할 다른 쓰기가 없다
 * ({@code token.respond()}는 승인·반려 전용). 동시 승인·반려와 레이스가 나도 이미 조립한 조회 응답을
 * 되돌리지 않게 한다. {@code markViewed}가 <b>read-write</b> 트랜잭션에 합류하므로
 * {@code @Transactional(readOnly = true)}에서 더티 변경이 flush 없이 버려지는 함정(PR #136 point 2)은
 * 그대로 피한다 — 오히려 더 보수적이다.
 *
 * <p><b>레이스 한계 (v1 허용)</b> — 호출자가 {@code getPublicView} 스냅샷에서 SENT를 본 뒤 그 사이
 * 승인이 커밋되면, {@code markViewed}는 무동작({@code Quote.markViewed} = 비-SENT면 return)하지만
 * NT-03은 헛발사될 수 있다. 동시 첫 열람이면 NT-03이 중복될 수도 있다. 완전 제거하려면
 * {@code QuoteCommand.markViewed}가 전이 수행 여부를 반환해야 하는데 경계 변경이라 v2로 미룬다.
 *
 * <p>호출자({@code CustomerQuoteService.view})는 여기서 나온 {@code RuntimeException}을 삼킨다 —
 * 조회(GET)가 부수효과 실패로 500이 되면 안 된다.
 *
 * <p>{@code CustomerQuoteService}와 <b>별 빈</b>이어야 {@code @Transactional} 프록시가 걸린다
 * (self-invocation 회피 — {@code MailOutcomeWriter} #112와 같은 패턴). 프록시 AOP는 public 메서드에만
 * 트랜잭션을 적용하므로 {@link #recordFirstView}는 public이다(클래스는 패키지 전용).
 */
@Component
@RequiredArgsConstructor
class FirstViewRecorder {

    private final QuoteCommand quoteCommand;
    private final NotificationCommand notificationCommand;
    private final CustomerNotificationMessages messages;

    /** 첫 열람 확정. 호출 전에 {@code status == "SENT"}를 확인하고 부른다. */
    @Transactional
    public void recordFirstView(QuoteQuery.PublicQuoteView view) {
        quoteCommand.markViewed(view.quoteId());
        notificationCommand.notifyForDeal(
                NotificationCommand.NotificationType.QUOTE_VIEWED,
                view.companyId(), view.dealId(),
                messages.quoteViewed(view.quoteNo()), view.quoteId());
    }
}
