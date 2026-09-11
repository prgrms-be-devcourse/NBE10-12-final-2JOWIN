package com.twojo.notification.service;

import com.twojo.boundary.MailCommand;
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
 * <p>{@code email_log.ref_id}에서 견적 id를 얻는다 — 의미가 template마다 다르다 ({@link #resolveQuoteId}):
 * {@code QUOTE_SENT}은 발송 토큰 id라 {@link ViewTokenQuery#quoteIdOf}로 되짚고, {@code QUOTE_REMIND}
 * (NT-05 배치)는 {@code ref_id}가 견적 id 그 자체다. 이후 {@link QuoteQuery#getPublicView}로 {@code dealId}
 * 까지 가고, 딜 담당자 규칙(Q-26 폴백)은 {@link NotificationCommand#notifyForDeal}이 해석한다.
 * {@code companyId}·{@code dealId}는 같은 {@code getPublicView} 결과에서 꺼내 짝을 맞춘다 (복합 FK 안전).
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
        UUID quoteId = resolveQuoteId(event);
        if (quoteId == null) {
            log.warn("EMAIL_FAILED 알림 건너뜀 - 견적 되짚기 실패, emailLogId={}", event.emailLogId());
            return;
        }
        QuoteQuery.PublicQuoteView view = quoteQuery.getPublicView(quoteId);
        notificationCommand.notifyForDeal(
                NotificationType.EMAIL_FAILED, view.companyId(), view.dealId(),
                message(event.templateType(), view.quoteNo()), quoteId);
    }

    /**
     * {@code email_log.ref_id}에서 견적 id를 얻는다 — template마다 {@code ref_id} 의미가 다르다 (docs/03 &sect;2.13).
     * {@code default} 없는 switch 식이라 {@code notifiesInApp}이 통과시킨 값이 여기 빠지면 컴파일이 막힌다.
     */
    private UUID resolveQuoteId(EmailDeliveryFailedEvent event) {
        return switch (event.templateType()) {
            case QUOTE_SENT -> viewTokenQuery.quoteIdOf(event.refId()).orElse(null); // ref_id = 발송 토큰 id
            case QUOTE_REMIND -> event.refId();                                      // ref_id = 견적 id 직접 (NT-05 배치)
            case QUOTE_EXPIRING -> event.refId();                                    // ref_id = 견적 id 직접 (NT-06 배치)
            case SIGNUP_APPROVED, SIGNUP_REJECTED, PASSWORD_RESET, INVITATION -> throw new IllegalStateException(
                    "EmailFailedNotificationWriter가 처리할 수 없는 template - notifiesInApp과 어긋남: " + event.templateType());
        };
    }

    private static String message(MailCommand.TemplateType type, String quoteNo) {
        return switch (type) {
            case QUOTE_SENT -> "[" + quoteNo + "] 견적서 발송 메일이 전송되지 않았습니다. 수신인을 확인하고 재발송해 주세요.";
            case QUOTE_REMIND -> "[" + quoteNo + "] 리마인드 안내 메일이 전송되지 않았습니다. 메일 채널을 확인해 주세요.";
            case QUOTE_EXPIRING -> "[" + quoteNo + "] 유효기간 임박 안내 메일이 전송되지 않았습니다. 고객에게 별도로 안내해 주세요.";
            case SIGNUP_APPROVED, SIGNUP_REJECTED, PASSWORD_RESET, INVITATION -> throw new IllegalStateException(
                    "message 미정의 template: " + type);
        };
    }
}
