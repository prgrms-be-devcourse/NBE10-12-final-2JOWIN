package com.twojo.notification.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link MailScheduled}를 호출자 커밋 후(AFTER_COMMIT)에 받아 {@link MailDispatcher}로 넘긴다.
 *
 * <p><b>{@code @Order(HIGHEST_PRECEDENCE)}</b> — 같은 트랜잭션의 다른 AFTER_COMMIT 리스너
 * (C의 감사 이벤트 등)가 먼저 던지면 뒤 리스너가 실행되지 않는다. 메일이 항상 먼저 제출되게 한다.
 *
 * <p><b>catch 범위가 {@code RuntimeException}이다</b> — 커밋은 이미 끝났고 사용자 요청은 성공이므로
 * 여기서 새는 예외를 HTTP로 올리지 않는다. 로그는 예외 클래스명만 남긴다(AsyncUncaughtExceptionHandler와
 * 같은 철학 — 메시지·인자에 수신자·토큰이 섞인다).
 * <ul>
 *   <li>{@code TaskRejectedException}(큐 포화) — 이 메일은 {@code body}를 이벤트로만 들고 있어 배치가
 *       재구성 못 한다. SCHEDULED로 두면 FAILED 지표(docs/14 §1.5)가 무력화되므로 {@link MailFailureRecorder}로
 *       즉시 FAILED로 기록한다.</li>
 *   <li>그 외 — 로그만. 남은 SCHEDULED 행은 NT-12 이슈의 정체 감지가 처리한다.</li>
 * </ul>
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
        } catch (TaskRejectedException e) {
            log.warn("메일 디스패치 제출 거부 — emailLogId={}, {}", event.emailLogId(), e.getClass().getName());
            mailFailureRecorder.markFailed(event.emailLogId());
        } catch (RuntimeException e) {
            log.error("메일 디스패치 리스너 실패 — emailLogId={}, {}", event.emailLogId(), e.getClass().getName());
        }
    }
}
