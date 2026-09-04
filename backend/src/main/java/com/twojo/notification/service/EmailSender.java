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
 *
 * <p>이 인터페이스는 package-private다 — 실제 어댑터도 <b>같은 패키지({@code notification.service})</b>에
 * 둔다. 하위 패키지에 두면 이 타입이 안 보인다(모듈이 아니라 패키지 경계 — {@code TokenGenerator} 리뷰에서
 * 팀이 밟은 함정). 다른 모듈에서 참조할 일은 없다.
 */
interface EmailSender {

    /**
     * 렌더 완료된 메일 1건을 발송한다. 실패는 예외로 알린다(디스패처가 잡아 {@code EmailLog.markFailed}).
     */
    void send(String recipientEmail, String subject, String body);
}
