package com.twojo.activity.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 다음 할 일 (AC-09) — 배정 컬럼 없음 (Q-29): deal의 순수 자식.
 * "내 할 일" = 내 담당 Deal의 미완료 할 일. 이관 시 Deal을 따라 자동 이동.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID dealId;

    private String content;

    private LocalDate dueDate;

    private Instant doneAt;

    /**
     * 다음 할 일 등록 (AC-09).
     *
     * <p>배정 대상이 없다 (Q-29) — Deal의 순수 자식이라 "내 할 일"은 내가 담당하는 Deal에서
     * 파생된다. 담당이 이관되면 할 일도 Deal을 따라 자동으로 옮겨간다.
     *
     * <p>{@code dueDate}는 필수다 — AC-09가 "할 일과 예정일"을 함께 요구하고,
     * {@code task.due_date}도 NOT NULL이다. 기한 없는 할 일은 만들어지지 않는다.
     */
    public static Task create(UUID dealId, String content, LocalDate dueDate) {
        Task task = new Task();
        task.dealId = Objects.requireNonNull(dealId, "dealId");
        task.content = Objects.requireNonNull(content, "content");
        task.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        return task;
    }

    /**
     * 할 일 수정 — <b>null로 온 필드는 바꾸지 않는다.</b>
     * {@code done} 하나만 토글하는 완료 처리가 주 용도라 부분 수정으로 확정했다.
     */
    public void update(String content, LocalDate dueDate) {
        if (content != null) {
            this.content = content;
        }
        if (dueDate != null) {
            this.dueDate = dueDate;
        }
    }

    /**
     * 완료 처리 — 이미 완료됐으면 무동작이라 최초 완료 시각이 유지된다.
     * 별도 상태 컬럼이 없어 {@code doneAt}의 null 여부가 곧 완료 여부다.
     */
    public void complete(Instant now) {
        if (doneAt == null) {
            this.doneAt = Objects.requireNonNull(now, "now");
        }
    }
}
