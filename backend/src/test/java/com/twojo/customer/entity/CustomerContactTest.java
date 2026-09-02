package com.twojo.customer.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerContactTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private CustomerContact 담당자() {
        return CustomerContact.create(CUSTOMER_ID, "이수정", "대리", "010-1111-2222",
                "sujeong@dodam.co.kr");
    }

    @Test
    @DisplayName("update()는 null로 온 필드를 바꾸지 않는다 (PATCH — 08 §B)")
    void update_nullFieldsUnchanged() {
        CustomerContact contact = 담당자();

        contact.update(null, null, "010-9999-8888", null);

        assertThat(contact.getPhone()).isEqualTo("010-9999-8888");
        assertThat(contact.getName()).isEqualTo("이수정");
        assertThat(contact.getTitle()).isEqualTo("대리");
        assertThat(contact.getEmail()).isEqualTo("sujeong@dodam.co.kr");
    }

    @Test
    @DisplayName("새 담당자는 대표가 아니고, markPrimary()/releasePrimary()로 전환된다 (CU-11)")
    void primaryFlag() {
        CustomerContact contact = 담당자();
        assertThat(contact.isPrimary()).isFalse();

        contact.markPrimary();
        assertThat(contact.isPrimary()).isTrue();

        contact.releasePrimary();
        assertThat(contact.isPrimary()).isFalse();
    }
}
