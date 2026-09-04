package com.twojo.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link EmailSender}의 개발/교육 환경 구현 — 실제로 보내지 않고 발송 사실만 로그로 남긴다.
 *
 * <p>SES/SMTP 자격이 준비되면(docs/14-tech-stack.md §3-3) 어댑터 빈으로 교체된다 —
 * 디스패처·{@code email_log} 경로는 그대로다. {@code @ConditionalOnProperty}로 스왑 지점을 열어 둔다:
 * {@code twojo.mail.sender}가 없거나 {@code log}면 이 빈이, 어댑터를 넣으면 그쪽이 유일한 {@link EmailSender}가
 * 된다(둘 다 무조건이면 {@code NoUniqueBeanDefinitionException}으로 부팅 실패).
 *
 * <p><b>로그에 남기는 것은 마스킹된 수신자와 제목뿐이다.</b> {@code body}에는 열람 링크의 원문 토큰이
 * 들어 있어(docs/14 §2-1·§7.3) 절대 찍지 않는다. 발송 단위 추적은 디스패처가 남기는 {@code emailLogId}로 한다.
 *
 * <p>예외를 던지지 않는다 — 이 구현엔 실패 경로가 없어 디스패처는 항상 {@code markSent}로 간다.
 */
@Component
@ConditionalOnProperty(name = "twojo.mail.sender", havingValue = "log", matchIfMissing = true)
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String recipientEmail, String subject, String body) {
        log.info("메일 발송(로그 전용) — to={}, subject={}", mask(recipientEmail), subject);
    }

    /** {@code sujeong@dodam.co.kr} → {@code su****@dodam.co.kr}. 로컬 파트가 2자 이하면 전부 가린다. */
    private static String mask(String email) {
        if (email == null) {
            return "<null>";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "****";
        }
        String local = email.substring(0, at);
        String head = local.length() <= 2 ? "" : local.substring(0, 2);
        return head + "****" + email.substring(at);
    }
}
