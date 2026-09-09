package com.twojo.notification.service;

import com.twojo.boundary.MailCommand;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * NT-12 — 최종 실패한 시스템 메일을 담당 구성원에게 인앱({@code EMAIL_FAILED})으로 알린다.
 *
 * <p>{@link EmailDeliveryFailedEvent}를 {@link MailOutcomeWriter}의 FAILED 커밋 뒤(AFTER_COMMIT)에 받는다.
 * 실제 쓰기는 {@link EmailFailedNotificationWriter}({@code REQUIRES_NEW})에 위임한다 —
 * {@code MailScheduledListener} &rarr; {@code MailOutcomeWriter}와 같은 두-빈 구조다.
 * {@link com.twojo.boundary.NotificationCommand}가 {@code MANDATORY}라 트랜잭션이 있어야 하고,
 * 알림 쓰기가 실패해도 이미 커밋된 {@code email_log} FAILED를 되돌리지 않아야 한다.
 *
 * <p><b>v1은 {@code QUOTE_SENT}만</b> 처리한다 (docs/03-requirements.md &sect;2.13) — NT-06 임박 배치는
 * 미구현, NT-01 초대는 A 흐름, NT-03~05 병행분은 원 수신자에게 인앱이 이미 갔고, NT-13·14는
 * "인앱 수신자 없음"으로 명문화됐다. 그 외 template은 {@code email_log} FAILED만 남긴다.
 *
 * <p><b>자기 예외는 삼킨다</b> — 알림은 부가 작업이고 실패 감지 바닥은 {@code email_log} FAILED다.
 * 로그엔 클래스명만 남긴다 (수신자·토큰이 예외 메시지에 섞인다, docs/14-tech-stack.md &sect;2-1·&sect;7.3).
 */
@Component
@RequiredArgsConstructor
class EmailFailedNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailFailedNotifier.class);

    private final EmailFailedNotificationWriter writer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EmailDeliveryFailedEvent event) {
        if (event.templateType() != MailCommand.TemplateType.QUOTE_SENT) {
            return;
        }
        try {
            writer.write(event);
        } catch (RuntimeException e) {
            log.warn("EMAIL_FAILED 알림 실패 - emailLogId={}, {}", event.emailLogId(), e.getClass().getName());
        }
    }
}
