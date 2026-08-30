package com.twojo.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.notification.entity.Notification.Type;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static Notification created() {
        return Notification.of(UUID.randomUUID(), UUID.randomUUID(), Type.QUOTE_VIEWED,
                "고객이 견적을 열람했습니다", "QUOTE", UUID.randomUUID());
    }

    @Test
    @DisplayName("of()로 만든 알림은 readAt이 null이다 (미읽음)")
    void of_unread() {
        assertThat(created().getReadAt()).isNull();
    }

    @Test
    @DisplayName("markRead() 최초 호출 시 읽은 시각을 기록한다 (NT-08)")
    void markRead_firstCall() {
        Notification notification = created();

        notification.markRead();

        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("markRead()를 재호출해도 최초 읽은 시각을 유지한다 (멱등)")
    void markRead_idempotent() {
        Notification notification = created();
        notification.markRead();
        Instant first = notification.getReadAt();

        notification.markRead();

        assertThat(notification.getReadAt()).isEqualTo(first);
    }
}
