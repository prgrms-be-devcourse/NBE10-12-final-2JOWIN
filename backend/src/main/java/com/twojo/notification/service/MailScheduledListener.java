package com.twojo.notification.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link MailScheduled}를 호출자 커밋 후(AFTER_COMMIT)에 받아 {@link MailDispatcher}로 넘긴다.
 *
 * <p><b>{@code @Order(HIGHEST_PRECEDENCE)}</b> — 같은 트랜잭션의 다른 AFTER_COMMIT 리스너
 * (C의 감사 이벤트 등)가 먼저 던지면 뒤 리스너가 실행되지 않는다. 메일이 항상 먼저 제출되게 한다.
 *
 * <p><b>{@code dispatch()}는 {@code @Async}라 이 호출 지점에서 나올 수 있는 예외는 "제출 실패"뿐이다.</b>
 * 큐 포화({@code TaskRejectedException})·셧다운 중 실행기 파괴({@code IllegalStateException}) 등 이유는
 * 달라도 결과는 같다 — 발송을 시도조차 못 했고 {@code body}는 이벤트에만 있어 재구성 불가. 전부 FAILED로
 * 닫는다({@link MailFailureRecorder}, docs/05 §11). SCHEDULED로 두면 FAILED 지표(docs/14 §1.5)가 무력화된다.
 *
 * <p><b>{@code catch (RuntimeException)}이 새는 예외를 HTTP로 올리지 않는다</b> — 커밋은 이미 끝났고
 * 사용자 요청은 성공이다. 로그는 예외 클래스명만 남긴다(AsyncUncaughtExceptionHandler와 같은 철학 —
 * 메시지·인자에 수신자·토큰이 섞인다). FAILED 기록마저 실패하면 그것도 삼킨다 — AFTER_COMMIT에서 새는
 * 예외는 커밋된 요청을 500으로 뒤집는다({@link #recordFailedQuietly}).
 *
 * <p><b>{@code @ApplicationModuleListener}를 쓰지 않는다</b> — 그것은 {@code @Async} +
 * {@code @Transactional(REQUIRES_NEW)} + {@code @TransactionalEventListener(AFTER_COMMIT)}를 한 메서드에
 * 합친 것이라, AsyncConfig javadoc이 금지한 형태(제출 거부를 메서드 안에서 못 잡음)다.
 */
@Component
@RequiredArgsConstructor
class MailScheduledListener {

    private static final Logger log = LoggerFactory.getLogger(MailScheduledListener.class);

    private final MailDispatcher mailDispatcher;
    private final MailFailureRecorder mailFailureRecorder;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MailScheduled event) {
        try {
            mailDispatcher.dispatch(event);
        } catch (RuntimeException e) {
            // 제출 실패 = 발송 시도조차 못 함. body는 이벤트에만 있어 재구성 불가 → FAILED로 닫는다 (docs/05 §11).
            log.warn("메일 디스패치 제출 실패 — emailLogId={}, {}", event.emailLogId(), e.getClass().getName());
            recordFailedQuietly(event.emailLogId());
        }
    }

    /** AFTER_COMMIT에서 새는 예외는 커밋된 요청을 500으로 뒤집는다 — 기록 실패는 여기서 끝낸다. */
    private void recordFailedQuietly(UUID emailLogId) {
        try {
            mailFailureRecorder.markFailed(emailLogId);
        } catch (RuntimeException e) {
            log.error("FAILED 기록 실패 — emailLogId={}, {}", emailLogId, e.getClass().getName());
        }
    }
}
