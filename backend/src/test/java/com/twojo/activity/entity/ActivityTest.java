package com.twojo.activity.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActivityTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final Instant OCCURRED = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-09-02T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-02T11:00:00Z");

    private Activity 상담기록() {
        return Activity.create(COMPANY_ID, DEAL_ID, AUTHOR_ID,
                Activity.Channel.CALL, "가격 협의 통화", OCCURRED);
    }

    @Test
    @DisplayName("update()는 null로 온 필드를 바꾸지 않는다 (PATCH — 08 §B)")
    void update_nullFieldsUnchanged() {
        Activity activity = 상담기록();

        activity.update(null, "가격 협의 통화 — 5% 할인 요청", null);

        assertThat(activity.getContent()).isEqualTo("가격 협의 통화 — 5% 할인 요청");
        assertThat(activity.getChannel()).isEqualTo(Activity.Channel.CALL);
        assertThat(activity.getOccurredAt()).isEqualTo(OCCURRED);
    }

    @Test
    @DisplayName("softDelete()를 재호출해도 최초 삭제 시각을 유지한다 (멱등)")
    void softDelete_idempotent() {
        Activity activity = 상담기록();

        activity.softDelete(T1);
        activity.softDelete(T2);

        assertThat(activity.getDeletedAt()).isEqualTo(T1);
    }
}
