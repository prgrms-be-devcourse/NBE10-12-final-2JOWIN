package com.twojo.customer.repository;

import com.twojo.customer.entity.CustomerContact;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 고객사 담당자 조회.
 *
 * <p><b>지금 있는 것</b> — 담당자가 특정 고객사 소속인지 판정하는 존재 검사 하나.
 * <p><b>나중에 올 것</b> — 담당자 등록·수정·대표 지정(CU-09~11)은 고객사 API 이슈에서 추가한다.
 *
 * <p>{@code customer_contact}에는 {@code company_id}가 없다 — 부모(고객사) 경유 격리이기 때문이다
 * (docs/06 설계 원칙). 따라서 회사 판정은 부모를 통해서만 가능하며, 담당자 단독 조회에는
 * 테넌트 조건을 걸 수 없다.
 */
public interface CustomerContactRepository extends JpaRepository<CustomerContact, UUID> {

    /**
     * 담당자가 해당 고객사 소속인지 판정 — 견적 수신인 검증의 재료다.
     * {@code customer_contact}에 복합 FK를 걸 수 없어 서비스 검증이 유일 방어다
     * (docs/06 "DB로 못 막는 것", CONTACT_NOT_IN_CUSTOMER).
     */
    boolean existsByIdAndCustomerId(UUID id, UUID customerId);
}
