package com.twojo.product.repository;

import com.twojo.product.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 카탈로그 조회 (PR) — 회사 공유 자원이라 담당 개념은 없고 회사 스코프만 건다 (PR-10, SC-01).
 * 상품에는 소프트 삭제가 없다 — 판매 중지(status)로 대체한다 (docs/11 §1.5).
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** 회사 스코프. 판매 중지 여부로 거르지 않는다 — 기존 견적 조회가 깨지면 안 된다 (PR-07). */
    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);
}
