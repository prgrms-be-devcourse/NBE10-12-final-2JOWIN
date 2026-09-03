package com.twojo.activity.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AuditActor;
import com.twojo.boundary.AuditActorType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Instant OCCURRED = Instant.parse("2026-09-02T10:00:00Z");

    private AuditLog 감사로그(AuditActor actor) {
        return AuditLog.of(COMPANY_ID, "QUOTE", QUOTE_ID, "QUOTE_SENT",
                actor, OCCURRED, "{\"quoteNo\":\"Q-2608-014\"}");
    }

    @Test
    @DisplayName("of()는 계정 있는 행위자를 타입·id 두 컬럼으로 옮긴다")
    void of_actorWithId() {
        AuditLog log = 감사로그(AuditActor.member(MEMBER_ID));

        assertThat(log.getActorType()).isEqualTo(AuditActorType.MEMBER);
        assertThat(log.getActorId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("of()는 계정 없는 행위자의 actorId를 null로 남긴다 (CUSTOMER_LINK·SYSTEM)")
    void of_actorWithoutId() {
        assertThat(감사로그(AuditActor.customerLink()).getActorType())
                .isEqualTo(AuditActorType.CUSTOMER_LINK);
        assertThat(감사로그(AuditActor.customerLink()).getActorId()).isNull();

        assertThat(감사로그(AuditActor.system()).getActorType())
                .isEqualTo(AuditActorType.SYSTEM);
        assertThat(감사로그(AuditActor.system()).getActorId()).isNull();
    }
}
