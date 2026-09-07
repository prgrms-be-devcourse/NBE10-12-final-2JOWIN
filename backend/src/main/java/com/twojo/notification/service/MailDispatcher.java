package com.twojo.notification.service;

import com.twojo.global.config.AsyncConfig;
import com.twojo.notification.entity.EmailLog;
import com.twojo.notification.repository.EmailLogRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 커밋 후 비동기 발송 — {@link MailScheduledListener}가 AFTER_COMMIT에 호출한다.
 * {@code email_log}를 id로 다시 조회해 발송하고, 결과를 {@link MailOutcomeWriter}에 넘겨 SENT/FAILED로 기록한다.
 *
 * <p><b>트랜잭션 경계</b> — {@code dispatch()}는 트랜잭션을 열지 않는다({@code @Async}만). {@code findById}는
 * 짧은 조회 한 번이고(엔티티는 detached — 스칼라 필드만 읽는다), {@code send()}+재시도 sleep은 네트워크 I/O라
 * 트랜잭션 밖에서 돈다(DB 커넥션 미점유). 결과 상태 쓰기만 {@link MailOutcomeWriter}가 {@code REQUIRES_NEW}로
 * 별도 커밋한다. #69는 메서드 전체를 {@code @Transactional(REQUIRES_NEW)}로 감쌌으나, 재시도 sleep이 그 안에
 * 들어가면 커넥션을 sleep 내내 쥐게 돼 분리했다(docs/05 §11 · NT-12 이슈).
 *
 * <p><b>재시도 1회</b> — {@code send()}가 {@code RuntimeException}을 던지면 {@link #retryDelay} 후 한 번 더
 * 시도한다. 단 {@code IllegalArgumentException}(잘못된 주소 등)은 재시도해도 같은 결과라 곧바로 실패로
 * 닫는다. 재시도 대기 중 스레드가 인터럽트되면(셧다운 신호)
 * 인터럽트 플래그를 복원하고 2차 시도를 포기한다 — 닫히는 컨텍스트에 {@code send()}를 또 걸어봐야 잡음만 낸다.
 * 예외 종류(SMTP 5xx 확정 실패 등)로 재시도를 거르는 건 {@code EmailSender} 계약 확장이 필요해 실 SMTP
 * 어댑터 이슈(docs/14 §3-3)로 미룬다.
 *
 * <p>엔티티·{@code SecurityContext}·요청 스코프를 넘겨받지 않는다 — {@code emailLogId}로 새로 조회한다.
 * {@code subject}·{@code body}만 이벤트에서 온다({@code email_log}에 없으므로).
 *
 * <p><b>이중 발송 방지</b>는 {@code send()} 전 {@code status != SCHEDULED} 가드가 담당한다.
 * {@link MailOutcomeWriter}의 재조회는 {@link EmailLog} 멱등 메서드가 방어선이다. v1엔 디스패치 경로가
 * 하나뿐이라 가드가 걸릴 일이 없다(정체 감지 배치는 v1.1).
 *
 * <p><b>{@code @Async}가 퇴화해 동기로 돌아도 안전하다</b> — {@link MailOutcomeWriter}가 {@code REQUIRES_NEW}라
 * AFTER_COMMIT 시점의 (이미 커밋된) 원 트랜잭션에 합류하지 않고 별도로 커밋한다.
 *
 * <p><b>결과 기록 실패는 삼킨다</b> — {@code markSent}/{@code markFailed}가 던져도 {@link #recordOutcome}가
 * 로그만 남기고 넘어간다. 행은 SCHEDULED로 남아 정체 감지 배치(v1.1) 대상이 된다.
 *
 * <p>로그는 예외 + {@code getCause()} 체인(최대 3단)의 <b>클래스명만</b> 남긴다 — 메시지·인자에는 수신자·
 * 원문 토큰이 섞인다(docs/14 §2-1·§7.3).
 */
@Component
@RequiredArgsConstructor
class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final EmailLogRepository emailLogRepository;
    private final EmailSender emailSender;
    private final MailOutcomeWriter outcomeWriter;

    /** 재시도 전 대기. 즉시 재시도는 일시 장애에 잘 안 먹는다. 워커 풀 스레드라 블로킹 OK. */
    private Duration retryDelay = Duration.ofSeconds(1);

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    public void dispatch(MailScheduled event) {
        EmailLog row = emailLogRepository.findById(event.emailLogId()).orElse(null);
        if (row == null) {
            log.warn("발송 대상 email_log 없음 — emailLogId={}", event.emailLogId());
            return;
        }
        if (row.getStatus() != EmailLog.Status.SCHEDULED) {
            return;
        }
        String recipientEmail = row.getRecipientEmail();   // detached 엔티티에서 캡처 — 이후 DB 접근 없음
        try {
            sendWithRetry(recipientEmail, event.subject(), event.body());
            recordOutcome(() -> outcomeWriter.markSent(event.emailLogId(), Instant.now()));
        } catch (RuntimeException e) {
            log.warn("메일 발송 재시도 실패 — emailLogId={}, {}", event.emailLogId(), causeChain(e));
            recordOutcome(() -> outcomeWriter.markFailed(event.emailLogId()));
        }
    }

    /**
     * 1차 발송 → 실패 시 {@link #retryDelay} 후 1회 재시도. {@code IllegalArgumentException}·
     * {@code NullPointerException}은 재시도하지 않고 그대로 던진다. 대기 중 인터럽트되면 플래그를 복원하고
     * 원래 예외를 던져 재시도를 포기한다. 2차도 실패하면 그 예외가 호출자로 전파된다.
     */
    private void sendWithRetry(String recipientEmail, String subject, String body) {
        try {
            emailSender.send(recipientEmail, subject, body);
        } catch (IllegalArgumentException deterministic) {
            throw deterministic;   // 잘못된 주소 등 — 재시도해도 같은 결과
        } catch (RuntimeException first) {
            log.warn("메일 발송 1차 실패, 재시도 — {}", causeChain(first));
            try {
                Thread.sleep(retryDelay.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw first;   // 셧다운 신호 — 2차 시도 포기
            }
            emailSender.send(recipientEmail, subject, body);   // 2차 — 던지면 호출자로 전파
        }
    }

    /** 결과 기록({@code REQUIRES_NEW})이 던져도 {@code @Async} 밖으로 새지 않게 삼킨다 — 행은 SCHEDULED로 남는다. */
    private void recordOutcome(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("발송 결과 기록 실패 — {}", e.getClass().getName());
        }
    }

    /** 예외 + cause 체인(최대 3단)의 클래스명. 메시지·인자는 담지 않는다 (수신자·원문 토큰 유출 방지). */
    private static String causeChain(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        Throwable cause = t.getCause();
        for (int depth = 0; cause != null && depth < 3; depth++) {
            sb.append(" <- ").append(cause.getClass().getName());
            cause = cause.getCause();
        }
        return sb.toString();
    }
}
