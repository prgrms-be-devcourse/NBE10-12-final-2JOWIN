package com.twojo.customer.repository;

import com.twojo.customer.entity.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 고객사 조회 (CU) — 모든 조회에 회사 스코프와 소프트 삭제 조건이 함께 걸린다 (SC-01, docs/11 §1.5).
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /** 회사 스코프 + 미삭제. 조건에 맞지 않으면 빈 Optional — 호출부에서 404로 변환한다 (SC-09). */
    Optional<Customer> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);
}
