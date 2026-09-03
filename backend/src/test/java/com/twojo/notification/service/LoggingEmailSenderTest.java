package com.twojo.notification.service;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link LoggingEmailSender} — 발송 사실은 남기되 {@code body}·원문 토큰·전체 수신자 주소는
 * 어떤 로그에도 나오지 않아야 한다(docs/14-tech-stack.md §2-1·§7.3). 하드 제약이라 회귀 방지로 고정한다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LoggingEmailSenderTest {

    private final LoggingEmailSender sender = new LoggingEmailSender();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void 로그를_가로챈다() {
        logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void 어펜더를_떼낸다() {
        logger.detachAppender(appender);
    }

    private String 남은_로그() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(joining("\n"));
    }

    @Test
    @DisplayName("제목은 남기고 본문·원문 토큰·전체 수신자 주소는 로그에 남기지 않는다")
    void 민감정보를_로그에_남기지_않는다() {
        String rawToken = "raw-token-SHOULD-NOT-APPEAR-1234";
        String body = "박지훈님, 아래 링크에서 견적서를 확인하실 수 있습니다.\n\n"
                + "http://localhost:5173/q/" + rawToken + "\n\n유효기간: 2026-12-31";

        sender.send("sujeong@dodam.co.kr", "[Q-2608-014] 견적서 열람 안내", body);

        String logged = 남은_로그();
        assertThat(logged).contains("[Q-2608-014] 견적서 열람 안내");
        assertThat(logged).doesNotContain(rawToken);
        assertThat(logged).doesNotContain(body);
        assertThat(logged).doesNotContain("sujeong@dodam.co.kr");
        assertThat(logged).contains("su****@dodam.co.kr");
    }

    @Test
    @DisplayName("null·빈 값에도 예외를 던지지 않는다 (디스패처가 항상 markSent로 가도록)")
    void 예외를_던지지_않는다() {
        assertThatCode(() -> sender.send(null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> sender.send("", "", "")).doesNotThrowAnyException();
    }
}
