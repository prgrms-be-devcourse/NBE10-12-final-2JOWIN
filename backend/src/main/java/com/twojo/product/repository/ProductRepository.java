package com.twojo.product.repository;

import com.twojo.product.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 카탈로그 조회 (PR) — 회사 공유 자원이라 담당 개념은 없고 회사 스코프만 건다 (PR-10, SC-01).
 * 상품에는 소프트 삭제가 없다 — 판매 중지(status)로 대체한다 (docs/11 §1.5).
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** 회사 스코프. 판매 중지 여부로 거르지 않는다 — 기존 견적 조회가 깨지면 안 된다 (PR-07). */
    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);

    /** 목록 — 상태 필터 없이 전부 (PR-03·10). 정렬은 호출부의 Pageable이 정한다 (name ASC 고정). */
    Page<Product> findByCompanyId(UUID companyId, Pageable pageable);

    /** 목록 — 상태로 거른다 (`?status=ACTIVE`). */
    Page<Product> findByCompanyIdAndStatus(UUID companyId, Product.Status status, Pageable pageable);

    /**
     * 등록 시 이름 중복 판정 (PR-02).
     *
     * <p><b>판매 중지 상품도 포함한다</b> — {@code UNIQUE(company_id, name)}가 상태와 무관하기
     * 때문이다. 중지한 이름으로 다시 등록하려 하면 409가 나고, 문구가 판매 재개를 안내한다.
     */
    boolean existsByCompanyIdAndName(UUID companyId, String name);

    /**
     * 수정 시 이름 중복 판정 — <b>자기 자신은 제외한다.</b>
     * 이름을 그대로 두고 단가만 고치는 경우가 흔한데, 자기 이름에 걸리면 수정 자체가 막힌다.
     */
    boolean existsByCompanyIdAndNameAndIdNot(UUID companyId, String name, UUID id);
}
