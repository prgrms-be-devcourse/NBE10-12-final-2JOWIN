package com.twojo.boundary;

import java.util.UUID;

/**
 * 상품 조회 계약 — 구현: B(product 모듈).
 * <p>C는 견적 작성 시 {@link #get}으로 카탈로그 값을 가져와 quote_item에 값 복사한다(QT-24).
 * product 테이블을 직접 조인하지 않는다. (docs/11-work-breakdown.md §3)
 */
public interface ProductQuery {

    /** 이름·단위·현재 단가 → QT-24 스냅샷 원천 */
    ProductSnapshot get(AccessContext ctx, UUID productId);

    /** PR-06 — 판매 중지 상품 차단 */
    boolean isSellable(AccessContext ctx, UUID productId);

    record ProductSnapshot(UUID id, String name, String unit, Long unitPrice) {}
}
