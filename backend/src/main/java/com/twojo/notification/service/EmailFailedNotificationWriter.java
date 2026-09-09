package com.twojo.notification.service;

import com.twojo.boundary.NotificationCommand;
import com.twojo.boundary.NotificationCommand.NotificationType;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.ViewTokenQuery;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NT-12 {@code EMAIL_FAILED} 알림의 실제 쓰기 — {@link EmailFailedNotifier}가 위임한다.
 *
 * <p><b>{@code REQUIRES_NEW}</b> — 리스너 진입 시점엔 바인드된 트랜잭션이 없고
 * ({@code MailOutcomeWriter}의 FAILED가 이미 커밋됨), {@link NotificationCommand}는 {@code MANDATORY}라
 * 새 트랜잭션이 필요하다. 이 트랜잭션이 실패해도 커밋된 {@code email_log} FAILED에는 영향이 없다.
 *
 * <p>{@code email_log.ref_id}(발송 토큰 id) &rarr; {@link ViewTokenQuery#quoteIdOf} &rarr; {@code quoteId}
 * &rarr; {@link QuoteQuery#getPublicView}로 {@code dealId}까지 되짚고, 딜 담당자 규칙(Q-26 폴백)은
 * {@link NotificationCommand#notifyForDeal}이 해석한다. {@code companyId}·{@code dealId}는
 * 같은 {@code getPublicView} 결과에서 꺼내 짝을 맞춘다 (복합 FK 안전).
 */
@Component
@RequiredArgsConstructor
class EmailFailedNotificationWriter {

    private static final Logger log = LoggerFactory.getLogger(EmailFailedNotificationWriter.class);

    private final ViewTokenQuery viewTokenQuery;
    private final QuoteQuery quoteQuery;
    private final NotificationCommand notificationCommand;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(EmailDeliveryFailedEvent event) {
        UUID quoteId = viewTokenQuery.quoteIdOf(event.refId()).orElse(null);
        if (quoteId == null) {
            log.warn("EMAIL_FAILED 알림 건너뜀 - 토큰 행 없음, emailLogId={}", event.emailLogId());
            return;
        }
        QuoteQuery.PublicQuoteView view = quoteQuery.getPublicView(quoteId);
        notificationCommand.notifyForDeal(
                NotificationType.EMAIL_FAILED, view.companyId(), view.dealId(), message(view.quoteNo()), quoteId);
    }

    private static String message(String quoteNo) {
        return "[" + quoteNo + "] 견적서 발송 메일이 전송되지 않았습니다. 수신인을 확인하고 재발송해 주세요.";
    }
}
