package com.twojo.customer.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Instant T1 = Instant.parse("2026-09-02T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-02T11:00:00Z");

    private Customer 고객사() {
        return Customer.create(COMPANY_ID, MEMBER_ID, "도담건설", "제조", "중소", "장기 거래처");
    }

    @Test
    @DisplayName("softDelete()를 재호출해도 최초 삭제 시각을 유지한다 (멱등)")
    void softDelete_idempotent() {
        Customer customer = 고객사();

        customer.softDelete(T1);
        customer.softDelete(T2);

        assertThat(customer.getDeletedAt()).isEqualTo(T1);
    }
}
