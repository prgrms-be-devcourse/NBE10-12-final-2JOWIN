package com.twojo.notification.service;

import com.twojo.boundary.DealQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.NotificationCommand;
import com.twojo.notification.entity.Notification;
import com.twojo.notification.repository.NotificationRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link NotificationCommand} 실구현 — {@code notification} 행을 만든다. 이벤트·비동기 없이 저장하고 끝.
 *
 * <p><b>{@code @Transactional(MANDATORY)}</b> — 호출자 트랜잭션 합류를 강제한다. 트랜잭션 밖이면
 * 알림만 따로 커밋돼 비즈니스 액션과 원자성이 깨지므로, 그 전에 {@code IllegalTransactionStateException}으로 터진다.
 *
 * <p>{@link #notifyForDeal}이 {@code DealQuery}·{@code MemberQuery}로 수신자를 해석한다 — Q-26 폴백과
 * NT-10 union을 이 한 곳에 두어 호출부(A1·A2·A3·B3)가 같은 규칙을 복제하지 않게 한다.
 * docs/03-requirements.md §2.13 타입별 수신자 표의 구현 위치다.
 *
 * <p>{@code message}는 {@code notification.message VARCHAR(500)}을 넘으면 잘라 저장한다 — 자르지 않으면
 * 정상 입력(1000자까지 허용되는 문의 등)이 flush 시점 {@code DataIntegrityViolationException}으로
 * 호출자 트랜잭션을 통째로 롤백시킨다(호출자가 try/catch로 감싸도 안 잡힘).
 */
@Service
@RequiredArgsConstructor
class NotificationCommandImpl implements NotificationCommand {

    private static final Logger log = LoggerFactory.getLogger(NotificationCommandImpl.class);

    /** notification.message VARCHAR(500) — PostgreSQL VARCHAR는 바이트가 아니라 문자 수다. */
    private static final int MESSAGE_MAX = 500;

    private final NotificationRepository notificationRepository;
    private final DealQuery dealQuery;
    private final MemberQuery memberQuery;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(NotificationType type, UUID companyId, UUID recipientMemberId,
                       String message, RefType refType, UUID refId) {
        write(companyId, recipientMemberId, type, message, refType, refId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyForDeal(NotificationType type, UUID companyId, UUID dealId,
                              String message, UUID quoteId) {
        if (type == NotificationType.EMAIL_FAILED) {
            throw new IllegalArgumentException("EMAIL_FAILED는 Deal 컨텍스트가 아니다 - notify()를 쓴다");
        }
        UUID assignee = dealQuery.assigneeIdOf(dealId);   // 살아있는 Deal은 담당자 항상 있음 (계약)
        Set<UUID> targets = new LinkedHashSet<>();
        if (memberQuery.isActive(assignee)) {
            targets.add(assignee);
        }
        if (type == NotificationType.INQUIRY_RECEIVED || targets.isEmpty()) {
            targets.addAll(memberQuery.findAdminIds(companyId));   // NT-10 union / Q-26 폴백
        }
        if (targets.isEmpty()) {
            // MB-11이 활성 관리자 존재를 보장하므로 정상 흐름엔 없다 - 방어선.
            log.warn("알림 수신자 없음 - type={}, dealId={}", type, dealId);
            return;
        }
        for (UUID target : targets) {
            write(companyId, target, type, message, RefType.QUOTE, quoteId);
        }
    }

    private void write(UUID companyId, UUID recipientMemberId, NotificationType type,
                       String message, RefType refType, UUID refId) {
        String safe = message.length() <= MESSAGE_MAX
                ? message
                : message.substring(0, MESSAGE_MAX - 1) + "…";
        if (safe.length() < message.length()) {
            log.warn("알림 message 절삭 - type={}, {}자에서 {}자", type, message.length(), safe.length());
        }
        notificationRepository.save(Notification.of(
                companyId, recipientMemberId, toEntityType(type), safe,
                refType == null ? null : refType.name(), refId));
    }

    /** 계약 enum → 엔티티 enum. exhaustive switch라 한쪽에 값이 늘면 컴파일이 막힌다. */
    private static Notification.Type toEntityType(NotificationType type) {
        return switch (type) {
            case QUOTE_VIEWED -> Notification.Type.QUOTE_VIEWED;
            case QUOTE_APPROVED -> Notification.Type.QUOTE_APPROVED;
            case QUOTE_REJECTED -> Notification.Type.QUOTE_REJECTED;
            case REMIND_NO_RESPONSE -> Notification.Type.REMIND_NO_RESPONSE;
            case INQUIRY_RECEIVED -> Notification.Type.INQUIRY_RECEIVED;
            case EMAIL_FAILED -> Notification.Type.EMAIL_FAILED;
        };
    }
}
