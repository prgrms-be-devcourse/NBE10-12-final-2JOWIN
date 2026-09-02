package com.twojo.product.dto;

import java.util.UUID;

/**
 * 상품 응답 (PR-03·10) — 카탈로그는 회사 공유 자원이라 담당 개념이 없다.
 *
 * <p>{@code status}는 {@code ACTIVE} / {@code DISCONTINUED} 두 값이다.
 * 상품에는 삭제 API가 없다 — 판매 중지로 대체한다 (11 §1.5).
 */
public record ProductResponse(
        UUID id,
        String name,
        String unit,
        Long unitPrice,
        String description,
        String status) {}
