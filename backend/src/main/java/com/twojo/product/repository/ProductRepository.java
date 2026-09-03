package com.twojo.product.repository;

import com.twojo.product.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 카탈로그 조회 (PR) — 회사 공유 자원이라 담당 개념 없이 회사 스코프만 건다 (PR-10, SC-01).
 * 소프트 삭제가 없다 — 판매 중지(status)로 대체한다 (11 §1.5).
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** 단건 — 판매 중지 여부로 거르지 않는다. 기존 견적 조회가 깨지면 안 된다 (PR-07). */
    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);

    /** 목록 — 상태 필터 없이 전부 (PR-03·10). 정렬은 호출부의 Pageable이 정한다. */
    Page<Product> findByCompanyId(UUID companyId, Pageable pageable);

    /** 목록 — 상태로 거른다 ({@code ?status=ACTIVE}). */
    Page<Product> findByCompanyIdAndStatus(UUID companyId, Product.Status status, Pageable pageable);

    /** 등록 시 이름 중복 판정 (PR-02) — {@code UNIQUE(company_id, name)}가 상태와 무관하므로 중지 상품도 포함한다. */
    boolean existsByCompanyIdAndName(UUID companyId, String name);

    /** 수정 시 이름 중복 판정 — 자기 자신은 제외한다. 이름을 그대로 두면 자기 이름에 걸려 수정이 막힌다. */
    boolean existsByCompanyIdAndNameAndIdNot(UUID companyId, String name, UUID id);
}
