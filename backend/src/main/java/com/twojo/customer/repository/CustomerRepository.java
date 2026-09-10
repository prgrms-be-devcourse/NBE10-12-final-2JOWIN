package com.twojo.customer.repository;

import com.twojo.customer.entity.Customer;
import java.util.Collection;
import java.util.List;
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

    /** id 묶음 배치 조회 — 회사 스코프 밖·삭제된 고객사는 결과에서 빠진다. */
    List<Customer> findByCompanyIdAndIdInAndDeletedAtIsNull(UUID companyId, Collection<UUID> ids);

    /**
     * 목록·검색 (CU-03·04) — 회사 스코프와 미삭제는 항상 걸고, keyword·industry는 null이면 조건에서 빠진다.
     *
     * <p><b>담당 축이 없다</b> — 고객사는 회사 공유 자원이라 영업 담당자도 회사 전체를 본다 (SC-03).
     * 그래서 스코프 분기가 없다.
     *
     * <p>선택 필터가 둘뿐이라 한 문장으로 둔다. 파생 쿼리로 쓰면 조합이 넷으로 늘어난다.
     * 정렬은 호출부의 Pageable이 정한다 (Q-39).
     *
     * <p><b>{@code escape '!'}가 붙은 이유</b> — 검색어의 {@code %}·{@code _}는 LIKE 와일드카드다.
     * 이스케이프하지 않으면 {@code ?keyword=%} 한 글자로 회사 전체가 나온다. 그래서 이 메서드는
     * <b>이미 이스케이프된 검색어</b>를 받는다 — 호출부가 {@code %}·{@code _}·{@code !} 앞에
     * {@code !}를 붙여 넘긴다. 표시 문자가 {@code !}인 것은 {@code lower()}가 바꾸지 못해서다.
     *
     * <p><b>{@code cast(:param as string)}은 장식이 아니다.</b> 값이 null이면 JDBC가 타입을 몰라
     * {@code bytea}로 바인딩해 PostgreSQL이 {@code function lower(bytea) does not exist}로 막는다.
     * 캐스트가 파라미터 타입을 고정한다.
     */
    @Query("""
            select c from Customer c
            where c.companyId = :companyId
              and c.deletedAt is null
              and (cast(:keyword as string) is null
                   or lower(c.name) like lower(concat('%', cast(:keyword as string), '%')) escape '!')
              and (cast(:industry as string) is null or c.industry = cast(:industry as string))
            """)
    Page<Customer> search(@Param("companyId") UUID companyId,
                          @Param("keyword") String keyword,
                          @Param("industry") String industry,
                          Pageable pageable);
}
