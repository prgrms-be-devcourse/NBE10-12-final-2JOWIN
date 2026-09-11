package com.twojo.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * {@link EmailSender}의 실발송 구현 — SMTP 프로토콜로 보낸다(#319). AWS SES도 SMTP 인터페이스를
 * 제공하므로 이 어댑터 하나로 커버된다 — 벤더 SDK에 묶이지 않는다.
 *
 * <p><b>{@code from}을 {@code spring.mail.username}과 분리한다.</b> SES의 SMTP username은
 * IAM에서 파생된 인증 토큰(형태: {@code AKIA...})이지 이메일 주소가 아니다 — 인증 계정을 그대로
 * From으로 쓰면 {@link MimeMessageHelper#setFrom}이 주소 파싱에서 즉시 실패해 모든 발송이
 * 예외 없이 100% 깨진다. 인증(SMTP AUTH)과 발신 identity(From 헤더)는 SES에서 구조적으로 별개다.
 *
 * <p><b>{@code SimpleMailMessage} 대신 {@link MimeMessageHelper}로 인코딩을 명시한다.</b>
 * 전자는 내부적으로 JVM 플랫폼 기본 인코딩에 기대 한글 제목·본문이 배포 환경에 따라 깨질 수 있다 —
 * {@code UTF-8}을 생성자에서 못 박으면 이 의존성이 사라진다(Subject의 RFC 2047 인코딩도 함께 처리됨).
 *
 * <p><b>예외를 세분화 번역하지 않는다.</b> {@code MimeMessageHelper}가 던지는 체크 예외
 * ({@link MessagingException})는 계약({@link EmailSender})이 언체크만 허용해 {@link MailPreparationException}으로
 * 감싸 그대로 던진다. {@code mailSender.send(MimeMessage)}가 던지는 {@code MailException}(전송 실패)은
 * 이미 언체크라 그대로 전파한다 — 재시도·FAILED 전이는 {@code MailDispatcher}가 맡는다. SMTP 5xx
 * 확정 실패를 종류별로 구분해 재시도를 거르는 건 그쪽 javadoc이 "실 SMTP 어댑터 이슈로 미룬다"고
 * 이미 명시한 범위라 여기서 손대지 않는다.
 */
@Component
@ConditionalOnProperty(name = "twojo.mail.sender", havingValue = "smtp")
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    /** {@code @RequiredArgsConstructor}를 쓰지 않는 이유는 {@code @Value} 파라미터가 있어서 — 이 저장소의 기존 컨벤션. */
    SmtpEmailSender(JavaMailSender mailSender, @Value("${twojo.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String recipientEmail, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(body, false);   // html=false — 계약상 본문은 항상 평문이다
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
        mailSender.send(message);
    }
}
