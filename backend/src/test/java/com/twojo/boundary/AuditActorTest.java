package com.twojo.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditActorTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    @DisplayName("member()는 MEMBER + actorId 조합을 만든다")
    void member_success() {
        AuditActor actor = AuditActor.member(ID);

        assertThat(actor.type()).isEqualTo(AuditActorType.MEMBER);
        assertThat(actor.actorId()).isEqualTo(ID);
    }

    @Test
    @DisplayName("platformAdmin()은 PLATFORM_ADMIN + actorId 조합을 만든다")
    void platformAdmin_success() {
        AuditActor actor = AuditActor.platformAdmin(ID);

        assertThat(actor.type()).isEqualTo(AuditActorType.PLATFORM_ADMIN);
        assertThat(actor.actorId()).isEqualTo(ID);
    }

    @Test
    @DisplayName("customerLink()는 actorId 없는 CUSTOMER_LINK를 만든다")
    void customerLink_success() {
        AuditActor actor = AuditActor.customerLink();

        assertThat(actor.type()).isEqualTo(AuditActorType.CUSTOMER_LINK);
        assertThat(actor.actorId()).isNull();
    }

    @Test
    @DisplayName("system()은 actorId 없는 SYSTEM을 만든다")
    void system_success() {
        AuditActor actor = AuditActor.system();

        assertThat(actor.type()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(actor.actorId()).isNull();
    }

    @Test
    @DisplayName("MEMBER에 actorId가 없으면 생성 시점에 IllegalArgumentException을 던진다")
    void member_withoutId() {
        assertThatThrownBy(() -> new AuditActor(AuditActorType.MEMBER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PLATFORM_ADMIN에 actorId가 없으면 생성 시점에 IllegalArgumentException을 던진다")
    void platformAdmin_withoutId() {
        assertThatThrownBy(() -> new AuditActor(AuditActorType.PLATFORM_ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CUSTOMER_LINK에 actorId가 있으면 생성 시점에 IllegalArgumentException을 던진다")
    void customerLink_withId() {
        assertThatThrownBy(() -> new AuditActor(AuditActorType.CUSTOMER_LINK, ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SYSTEM에 actorId가 있으면 생성 시점에 IllegalArgumentException을 던진다")
    void system_withId() {
        assertThatThrownBy(() -> new AuditActor(AuditActorType.SYSTEM, ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("type이 null이면 NullPointerException을 던진다")
    void nullType() {
        assertThatThrownBy(() -> new AuditActor(null, ID))
                .isInstanceOf(NullPointerException.class);
    }
}
