package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

/**
 * {@link MailScheduledListener} — AFTER_COMMIT에서 디스패처를 부르고, 새는 예외를 HTTP로 올리지 않는다.
 * {@code dispatch()}가 던지는 예외(제출 실패)는 종류와 무관하게 그 행을 FAILED로 기록하며,
 * 기록마저 실패해도 {@code handle()}은 예외를 전파하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailScheduledListenerTest {

    private static final UUID EMAIL_LOG_ID = UUID.fromString("9d000000-0000-4000-8000-000000000001");

    @Mock
    private MailDispatcher mailDispatcher;
    @Mock
    private MailFailureRecorder mailFailureRecorder;
    @InjectMocks
    private MailScheduledListener listener;

    private static MailScheduled event() {
        return new MailScheduled(EMAIL_LOG_ID, "subject", "body");
    }

    @Test
    @DisplayName("제출 거부(TaskRejectedException)면 삼키고 그 행을 FAILED로 기록한다")
    void 제출_거부는_FAILED로_기록() {
        willThrow(new TaskRejectedException("queue full")).given(mailDispatcher).dispatch(any());

        assertThatCode(() -> listener.handle(event())).doesNotThrowAnyException();
        verify(mailFailureRecorder).markFailed(EMAIL_LOG_ID);
    }

    @Test
    @DisplayName("그 밖의 RuntimeException도 제출 실패이므로 FAILED로 기록한다")
    void 그_밖의_예외도_FAILED로_기록() {
        willThrow(new IllegalStateException("shutting down")).given(mailDispatcher).dispatch(any());

        assertThatCode(() -> listener.handle(event())).doesNotThrowAnyException();
        verify(mailFailureRecorder).markFailed(EMAIL_LOG_ID);
    }

    @Test
    @DisplayName("FAILED 기록마저 던져도 handle()은 예외를 전파하지 않는다 (커밋된 요청 500 방지)")
    void 기록_실패도_전파하지_않는다() {
        willThrow(new TaskRejectedException("queue full")).given(mailDispatcher).dispatch(any());
        willThrow(new RuntimeException("DB down")).given(mailFailureRecorder).markFailed(any());

        assertThatCode(() -> listener.handle(event())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("디스패치가 정상이면 FAILED 기록을 하지 않는다")
    void 정상이면_기록_안_함() {
        listener.handle(event());

        verify(mailFailureRecorder, never()).markFailed(any());
    }
}
