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
 * <p><b>template별 인앱 알림 여부는 {@link #notifiesInApp}에 있다</b> (docs/03-requirements.md &sect;2.13
 * NT-12 표). {@code default} 없는 switch 식이라 {@code TemplateType} 값이 늘면 컴파일이 막혀 "이 메일
 * 실패에 인앱 수신자가 있나"를 강제로 답하게 한다 — {@code !=} 비교였던 시절 {@code QUOTE_REMIND}가
 * 신호 없이 누락된 자리다 (#247). {@code QUOTE_SENT}(고객 대상)와 {@code QUOTE_REMIND}(NT-05 병행분,
 * &sect;2.13 "메일 채널 이상 인지용")를 알린다. 그 외는 {@code email_log} FAILED 지표만 남는다.
 *
 * <p><b>자기 예외는 삼킨다</b> — 알림은 부가 작업이고 실패 감지 바닥은 {@code email_log} FAILED다.
 * 로그엔 클래스명과 {@code templateType}만 남긴다 (수신자·토큰이 예외 메시지에 섞인다,
 * docs/14-tech-stack.md &sect;2-1·&sect;7.3). {@code templateType}은 PII가 아니라 정합 깨짐 진단에 필요하다.
 */
@Component
@RequiredArgsConstructor
class EmailFailedNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailFailedNotifier.class);

    private final EmailFailedNotificationWriter writer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EmailDeliveryFailedEvent event) {
        if (!notifiesInApp(event.templateType())) {
            return;
        }
        try {
            writer.write(event);
        } catch (RuntimeException e) {
            log.warn("EMAIL_FAILED 알림 실패 - emailLogId={}, templateType={}, {}",
                    event.emailLogId(), event.templateType(), e.getClass().getName());
        }
    }

    /**
     * 이 template 메일의 최종 실패에 인앱 {@code EMAIL_FAILED} 수신자가 있는가 (docs/03-requirements.md &sect;2.13
     * NT-12 표). {@code default} 없는 switch 식이라 {@code TemplateType} 값 추가 시 컴파일이 막힌다 — 정책 결정 누락 방지.
     */
    private static boolean notifiesInApp(MailCommand.TemplateType type) {
        return switch (type) {
            case QUOTE_SENT -> true;         // 딜 담당 구성원, Q-26 폴백 (NT-12 v1, #213)
            case QUOTE_REMIND -> true;       // NT-05 병행분 — 메일 채널 이상 인지용, 원 수신 구성원 = 딜 담당자 (§2.13, #247)
            case QUOTE_EXPIRING -> true;     // NT-06 임박 배치 — 고객사 담당자는 계정 없음, 실패 통보는 딜 담당 구성원 (§2.13, #249)
            case INVITATION,                 // 별건 — invited_by 되짚기 통로 필요, E 별도 채번
                 PASSWORD_RESET,            // NT-14 — 수신자 로그인 불가 (§2.13 "인앱 수신자 없음")
                 SIGNUP_APPROVED,           // NT-13 — 사내 인앱 수신자 없음
                 SIGNUP_REJECTED -> false;
        };
    }
}
