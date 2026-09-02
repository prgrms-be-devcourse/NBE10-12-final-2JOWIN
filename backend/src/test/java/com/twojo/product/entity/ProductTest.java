package com.twojo.product.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();

    private Product 상품() {
        return Product.create(COMPANY_ID, "A4 복사용지", "박스", 25_000L, "80g 2500매");
    }

    @Test
    @DisplayName("새 상품은 판매 중이고, discontinue()/reactivate()로 전환된다 (PR-05)")
    void statusTransition() {
        Product product = 상품();
        assertThat(product.getStatus()).isEqualTo(Product.Status.ACTIVE);

        product.discontinue();
        assertThat(product.getStatus()).isEqualTo(Product.Status.DISCONTINUED);

        product.reactivate();
        assertThat(product.getStatus()).isEqualTo(Product.Status.ACTIVE);
    }

    @Test
    @DisplayName("이미 판매 중지된 상품에 discontinue()를 재호출해도 예외 없이 그대로다 (멱등)")
    void discontinue_idempotent() {
        Product product = 상품();

        product.discontinue();
        product.discontinue();

        assertThat(product.getStatus()).isEqualTo(Product.Status.DISCONTINUED);
    }
}
