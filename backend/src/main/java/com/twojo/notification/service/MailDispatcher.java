package com.twojo.notification.service;

import com.twojo.global.config.AsyncConfig;
import com.twojo.notification.entity.EmailLog;
import com.twojo.notification.repository.EmailLogRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커밋 후 비동기 발송 — {@link MailScheduledListener}가 AFTER_COMMIT에 호출한다.
 * {@code email_log}를 id로 다시 조회해 발송하고 결과를 SENT/FAILED로 기록한다.
 *
 * <p>{@code @Async}(전용 실행기) + {@code REQUIRES_NEW} — 리스너와 별도 빈이어야 제출 거부
 * ({@code TaskRejectedException})가 리스너의 {@code try}에서 잡힌다(AsyncConfig javadoc §6).
 * 새 트랜잭션인 이유: AFTER_COMMIT 시점엔 원래 트랜잭션 동기화 정리가 아직 안 끝났을 수 있다.
 *
 * <p>엔티티·{@code SecurityContext}·요청 스코프를 넘겨받지 않는다 — {@code emailLogId}로 새로 조회한다.
 * {@code subject}·{@code body}만 이벤트에서 온다({@code email_log}에 없으므로).
 *
 * <p>이번 범위는 <b>1회 시도</b>다. 재시도·인앱 알림은 NT-12 이슈.
 */
@Component
@RequiredArgsConstructor
class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final EmailLogRepository emailLogRepository;
    private final EmailSender emailSender;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(MailScheduled event) {
        EmailLog row = emailLogRepository.findById(event.emailLogId()).orElse(null);
        if (row == null) {
            log.warn("발송 대상 email_log 없음 — emailLogId={}", event.emailLogId());
            return;
        }
        if (row.getStatus() != EmailLog.Status.SCHEDULED) {
            // 이벤트와 (NT-12 이슈의) 재처리 배치가 같은 행을 집는 경합 방어 — 이번 범위엔 경로가 하나뿐이라 휴면.
            return;
        }
        try {
            emailSender.send(row.getRecipientEmail(), event.subject(), event.body());
            row.markSent(Instant.now());
        } catch (RuntimeException e) {
            row.markFailed();
            log.warn("메일 발송 실패 — emailLogId={}, {}", event.emailLogId(), e.getClass().getName());
        }
    }
}
