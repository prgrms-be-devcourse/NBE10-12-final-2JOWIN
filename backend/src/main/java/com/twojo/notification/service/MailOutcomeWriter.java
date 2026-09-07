package com.twojo.notification.service;

import com.twojo.notification.entity.EmailLog;
import com.twojo.notification.repository.EmailLogRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code email_log}의 발송 결과(SENT/FAILED)를 {@code REQUIRES_NEW}로 독립 커밋한다.
 *
 * <p>두 곳이 호출한다:
 * <ul>
 *   <li>{@link MailDispatcher} — 커밋 후 비동기 발송의 성공/실패. {@code send()}는 네트워크 I/O라
 *       트랜잭션 밖에서 돌고(재시도 sleep이 DB 커넥션을 쥐지 않게, docs/05 §11), 결과 상태 쓰기만 여기서
 *       별도 커밋한다.</li>
 *   <li>{@link MailScheduledListener} — 디스패치 제출조차 못 한 경우({@code TaskRejectedException} 등)의 FAILED.</li>
 * </ul>
 *
 * <p><b>별도 빈인 이유</b>: 호출 지점이 {@code @Async} 워커거나 AFTER_COMMIT 리스너라 바인드된 트랜잭션이
 * 없거나 정리 중이다. {@code REQUIRES_NEW}로 새 트랜잭션을 열어야 하는데, 호출자 자기 메서드에
 * {@code @Transactional}을 붙이면 자기호출이라 프록시를 안 지난다.
 *
 * <p>{@code findById} 후 대상이 없으면 조용히 무동작한다. 상태 전이는 {@link EmailLog}의 멱등 메서드에
 * 맡긴다 — {@code markSent}: 이미 SENT면 무동작 / {@code markFailed}: 이미 SENT면 무동작.
 */
@Component
@RequiredArgsConstructor
class MailOutcomeWriter {

    private final EmailLogRepository emailLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID emailLogId, Instant sentAt) {
        emailLogRepository.findById(emailLogId).ifPresent(row -> row.markSent(sentAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID emailLogId) {
        emailLogRepository.findById(emailLogId).ifPresent(EmailLog::markFailed);
    }
}
