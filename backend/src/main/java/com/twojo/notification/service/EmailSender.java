package com.twojo.notification.service;

/**
 * 시스템 메일을 실제로 내보내는 통로 — 커밋 후 비동기 디스패처가 호출한다.
 *
 * <p>구현은 환경별로 교체된다: 개발/교육 환경은 {@link LoggingEmailSender}(로그만), 운영은
 * SES/SMTP 어댑터(docs/14-tech-stack.md §3-3). {@code email_log} 상태 갱신(SENT/FAILED)은
 * 디스패처 책임이라 이 인터페이스는 발송만 맡는다 — 성공이면 반환, 실패면 예외를 던진다.
 *
 * <p>{@code body}에는 열람 링크(원문 토큰)가 들어 있다 — 구현은 이 값을 로그·저장소에 남기면 안 된다
 * (docs/14-tech-stack.md §2-1·§7.3).
 */
interface EmailSender {

    /**
     * 렌더 완료된 메일 1건을 발송한다. 실패는 예외로 알린다(디스패처가 잡아 {@code EmailLog.markFailed}).
     */
    void send(String recipientEmail, String subject, String body);
}
