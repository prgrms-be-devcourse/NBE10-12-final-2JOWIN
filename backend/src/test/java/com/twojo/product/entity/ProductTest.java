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
    @DisplayName("update()는 null로 온 필드를 바꾸지 않는다 — 단가만 수정 (PATCH — 08 §B)")
    void update_skipsNulls() {
        Product product = 상품();

        product.update(null, null, 27_000L, null);

        assertThat(product.getName()).isEqualTo("A4 복사용지");
        assertThat(product.getUnit()).isEqualTo("박스");
        assertThat(product.getUnitPrice()).isEqualTo(27_000L);
        assertThat(product.getDescription()).isEqualTo("80g 2500매");
    }

    @Test
    @DisplayName("update()는 빈 문자열은 반영한다 — 설명을 비우는 경로다")
    void update_appliesBlank() {
        Product product = 상품();

        product.update(null, null, null, "");

        assertThat(product.getDescription()).isEmpty();
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
