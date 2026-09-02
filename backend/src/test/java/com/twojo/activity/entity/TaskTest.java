package com.twojo.activity.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final LocalDate DUE = LocalDate.of(2026, 9, 10);
    private static final Instant T1 = Instant.parse("2026-09-02T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-02T11:00:00Z");

    private Task 할일() {
        return Task.create(DEAL_ID, "견적서 재발송", DUE);
    }

    @Test
    @DisplayName("새 할 일은 미완료다 — doneAt이 null")
    void create_notDone() {
        assertThat(할일().getDoneAt()).isNull();
    }

    @Test
    @DisplayName("update()는 null로 온 필드를 바꾸지 않는다 — 08 §B에 주석이 없는 유일한 예외")
    void update_nullFieldsUnchanged() {
        Task task = 할일();

        task.update(null, LocalDate.of(2026, 9, 15));

        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(task.getContent()).isEqualTo("견적서 재발송");
    }

    @Test
    @DisplayName("complete()를 재호출해도 최초 완료 시각을 유지한다 (멱등)")
    void complete_idempotent() {
        Task task = 할일();

        task.complete(T1);
        task.complete(T2);

        assertThat(task.getDoneAt()).isEqualTo(T1);
    }
}
