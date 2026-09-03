package com.twojo.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 상품 등록 요청 (PR-01·02) — 기업 관리자만 (PR-09).
 *
 * <p>{@code name}은 회사 내 유일해야 한다. <b>판매 중지된 상품도 포함</b>이라
 * 중지한 이름으로 다시 등록하면 409 {@code PRODUCT_NAME_DUPLICATED}가 난다 —
 * 재등록이 아니라 판매 재개를 쓴다.
 *
 * <p>{@code unitPrice}는 <b>세전</b>이다 (Q-46). 부가세는 견적 계산에서 붙는다 (QT-22).
 */
public record CreateProductRequest(
        @NotBlank String name,
        @NotBlank String unit,
        @NotNull @PositiveOrZero Long unitPrice,
        String description) {}
