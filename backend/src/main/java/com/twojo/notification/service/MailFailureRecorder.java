package com.twojo.notification.service;

import com.twojo.notification.entity.EmailLog;
import com.twojo.notification.repository.EmailLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 디스패치가 시작조차 못 한 메일을 {@code email_log}에 FAILED로 기록한다 — {@link MailScheduledListener}가
 * {@code TaskRejectedException}(큐 포화)을 잡았을 때 호출한다.
 *
 * <p>별도 빈인 이유: 호출 지점이 AFTER_COMMIT 리스너라 원래 트랜잭션 동기화 정리가 안 끝났을 수 있어
 * {@code REQUIRES_NEW}로 독립 커밋해야 하는데, 리스너 자기 메서드에 {@code @Transactional}을 붙이면
 * 자기호출이라 프록시를 안 지난다.
 */
@Component
@RequiredArgsConstructor
class MailFailureRecorder {

    private final EmailLogRepository emailLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID emailLogId) {
        emailLogRepository.findById(emailLogId).ifPresent(EmailLog::markFailed);
    }
}
