package com.twojo.customer.repository;

import com.twojo.customer.entity.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 고객사 조회 (CU) — 모든 조회에 회사 스코프와 소프트 삭제 조건이 함께 걸린다 (SC-01, docs/11 §1.5).
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /** 회사 스코프 + 미삭제. 조건에 맞지 않으면 빈 Optional — 호출부에서 404로 변환한다 (SC-09). */
    Optional<Customer> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

    /**
     * 목록·검색 (CU-03·04) — 회사 스코프와 미삭제는 항상 걸고, keyword·industry는 null이면 조건에서 빠진다.
     *
     * <p><b>담당 축이 없다</b> — 고객사는 회사 공유 자원이라 영업 담당자도 회사 전체를 본다 (SC-03).
     * Deal 목록과 달리 스코프 분기가 없는 이유다.
     *
     * <p>선택 필터가 둘뿐이라 한 문장으로 둔다. {@code DealRepository}가 Specification으로 간 것은
     * 필터가 셋이라 파생 쿼리 조합이 폭발해서다 — 여기서는 그 비용이 나오지 않는다.
     * 정렬은 호출부의 Pageable이 정한다 (Q-39).
     */
    @Query("""
            select c from Customer c
            where c.companyId = :companyId
              and c.deletedAt is null
              and (:keyword is null or lower(c.name) like lower(concat('%', :keyword, '%')))
              and (:industry is null or c.industry = :industry)
            """)
    Page<Customer> search(@Param("companyId") UUID companyId,
                          @Param("keyword") String keyword,
                          @Param("industry") String industry,
                          Pageable pageable);
}
