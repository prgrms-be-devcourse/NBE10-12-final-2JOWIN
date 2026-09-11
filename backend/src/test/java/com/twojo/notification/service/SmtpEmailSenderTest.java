package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * {@link SmtpEmailSender} — {@code JavaMailSender}는 목이지만, {@code createMimeMessage()}는
 * 실제 {@code Session}으로 만든 진짜 {@link MimeMessage}를 돌려주게 해 {@link
 * org.springframework.mail.javamail.MimeMessageHelper}의 주소 파싱·인코딩을 실제 jakarta.mail
 * 로직으로 태운다 — {@code MessagingException} 발생을 목으로 흉내내지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SmtpEmailSenderTest {

    private static final String FROM = "no-reply@twojo.test";

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new SmtpEmailSender(mailSender, FROM);
    }

    private static MimeMessage realMimeMessage() {
        return new MimeMessage(Session.getDefaultInstance(new Properties()));
    }

    @Test
    @DisplayName("정상 발송 - JavaMailSender.send(MimeMessage)를 호출한다")
    void 정상_발송() {
        given(mailSender.createMimeMessage()).willReturn(realMimeMessage());

        sender.send("sujeong@dodam.test", "제목", "본문");

        then(mailSender).should().send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("본문·제목을 UTF-8로 인코딩한다 - 플랫폼 기본 인코딩에 기대지 않는다")
    void 본문은_UTF8로_인코딩() throws Exception {
        MimeMessage message = realMimeMessage();
        given(mailSender.createMimeMessage()).willReturn(message);

        sender.send("sujeong@dodam.test", "유효기간 임박 안내", "본문입니다");

        // getContentType()은 저장 전 값이라 charset이 안 드러난다 — 실제 발신 바이트를 직렬화해 확인한다.
        message.saveChanges();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);

        // SimpleMailMessage 경로였다면 JVM 기본 charset에 기댔을 자리 — 여기선 명시된 UTF-8이 실제로 찍힌다
        assertThat(raw.toString(StandardCharsets.UTF_8)).containsIgnoringCase("charset=UTF-8");
    }

    @Test
    @DisplayName("전송 실패(MailException)는 번역 없이 그대로 전파한다")
    void 전송_실패는_그대로_전파() {
        given(mailSender.createMimeMessage()).willReturn(realMimeMessage());
        willThrow(new MailSendException("SMTP down")).given(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send("sujeong@dodam.test", "제목", "본문"))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("주소 파싱이 실패하면 MessagingException을 MailPreparationException으로 감싼다")
    void 잘못된_주소는_MailPreparationException으로_감싼다() {
        given(mailSender.createMimeMessage()).willReturn(realMimeMessage());

        // '@' 두 개 — RFC822 구조 위반이라 InternetAddress 파싱이 확정적으로 던진다(사실상 확률 아님)
        assertThatThrownBy(() -> sender.send("a@b@c", "제목", "본문"))
                .isInstanceOf(MailPreparationException.class)
                .hasCauseInstanceOf(AddressException.class);

        then(mailSender).should(never()).send(any(MimeMessage.class));
    }
}
