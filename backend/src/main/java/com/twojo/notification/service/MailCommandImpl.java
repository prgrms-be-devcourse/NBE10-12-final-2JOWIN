package com.twojo.notification.service;

import com.twojo.boundary.MailCommand;
import com.twojo.notification.entity.EmailLog;
import com.twojo.notification.repository.EmailLogRepository;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MailCommand} 실구현 — {@code email_log}에 SCHEDULED 행을 만들고 {@link MailScheduled}를
 * 같은 트랜잭션에서 발행한다. 실제 발송은 {@link MailScheduledListener}가 커밋 후 비동기로 태운다.
 *
 * <p><b>{@code @Transactional(MANDATORY)}</b> — 호출자 트랜잭션에 합류를 강제한다. 트랜잭션 밖에서
 * 불리면 {@code save()}는 자기 트랜잭션으로 커밋되지만 {@code publishEvent()}는 활성 동기화가 없어
 * {@code @TransactionalEventListener}가 조용히 버린다(커밋된 SCHEDULED 행 + 안 나가는 메일 + 흔적 없음).
 * MANDATORY면 그 전에 {@code IllegalTransactionStateException}으로 즉시 터진다.
 *
 * <p>{@code refId}가 발송마다 유일하므로(계약) 사전 중복 체크 없이 항상 INSERT한다. 혹시 겹치면
 * {@code uk_email_log_dedup} 위반이 호출자 커밋 시점에 전파돼 롤백된다. 동기 실패는 삼키지 않는다.
 *
 * <p>{@code companyId}는 {@code SIGNUP_APPROVED} 계열만 null이다(계약). 그 외 null은 "회사 스코프
 * 조회·실패 지표에서 안 보이는 행"이 되므로 조용히 통과시키지 않고 즉시 던진다 — 처리되지 않은 예외는
 * {@code Exception} 폴백이 500 {@code INTERNAL_ERROR}로 닫는다.
 *
 * <p>{@code body}(원문 토큰 포함)는 {@code email_log}에 저장하지 않는다(docs/14 §2-1·§7.3) —
 * 이벤트에만 실어 디스패처가 1회 소비한다.
 */
@Service
@RequiredArgsConstructor
class MailCommandImpl implements MailCommand {

    private final EmailLogRepository emailLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void schedule(TemplateType type, UUID companyId, String recipientEmail,
                         UUID refId, String subject, String body) {
        if (type != TemplateType.SIGNUP_APPROVED) {
            Objects.requireNonNull(companyId, "companyId");   // 계약: SIGNUP_APPROVED 계열만 null
        }
        EmailLog row = emailLogRepository.save(
                EmailLog.schedule(companyId, type, recipientEmail, refId));
        eventPublisher.publishEvent(new MailScheduled(row.getId(), subject, body));
    }
}
