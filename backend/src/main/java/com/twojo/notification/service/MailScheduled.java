package com.twojo.notification.service;

import java.util.UUID;

/**
 * "메일 1건이 예약됐다" — {@link MailCommandImpl#schedule}이 {@code email_log} SCHEDULED 행을 저장하며
 * 같은 트랜잭션에서 발행한다. {@link MailScheduledListener}가 AFTER_COMMIT에 받아 발송을 태운다.
 *
 * <p><b>{@code subject}·{@code body}를 싣는다</b> — AsyncConfig가 규정한 "이벤트는 emailLogId만"의
 * 명시적 예외다(계약 소유자 승인). {@code body}(원문 토큰 포함)는 {@code email_log}에 저장하지 않으므로
 * (docs/14 §2-1·§7.3) 디스패처가 얻을 경로가 이 이벤트뿐이다. 리스너→디스패처 1회 소비 후 버리며
 * 어디서도 로깅하지 않는다.
 */
record MailScheduled(UUID emailLogId, String subject, String body) {
}
