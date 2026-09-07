package com.twojo.customer.repository;

import com.twojo.customer.entity.CustomerContact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 고객사 담당자 조회.
 *
 * <p>{@code customer_contact}에는 {@code company_id}가 없다 — 부모(고객사) 경유 격리이기 때문이다
 * (docs/06 설계 원칙). 따라서 회사 판정은 부모를 통해서만 가능하며, 담당자 단독 조회에는
 * 테넌트 조건을 걸 수 없다. <b>여기의 모든 조회가 {@code customerId}를 함께 받는 이유다</b> —
 * 서비스는 부모 고객사를 회사 스코프로 먼저 찾은 뒤에만 이 메서드들을 부른다.
 *
 * <p><b>{@code findById}를 쓰지 않는다</b> — 부모를 거치지 않아 타사 담당자가 그대로 나온다.
 *
 * <p>소프트 삭제가 없다 — 삭제는 차단 규칙(CU-14, PRIMARY_CONTACT_REQUIRED)을 통과하면 실제로 지운다.
 */
public interface CustomerContactRepository extends JpaRepository<CustomerContact, UUID> {

    /**
     * 담당자가 해당 고객사 소속인지 판정 — 견적 수신인 검증의 재료다.
     * {@code customer_contact}에 복합 FK를 걸 수 없어 서비스 검증이 유일 방어다
     * (docs/06 "DB로 못 막는 것", CONTACT_NOT_IN_CUSTOMER).
     */
    boolean existsByCustomerIdAndId(UUID customerId, UUID id);

    /**
     * 고객사 상세의 담당자 목록 (CU-05·09) — 대표가 먼저, 그다음 이름순이다.
     * 화면이 대표 카드를 맨 앞에 별 배지로 두는 것을 그대로 따른다 (docs/10 담당자 카드).
     */
    List<CustomerContact> findByCustomerIdOrderByIsPrimaryDescNameAsc(UUID customerId);

    /**
     * 부모 경유 단건 — 수정·대표 지정이 쓴다.
     * {@code cid}가 그 고객사 소속이 아니면 빈 Optional, 호출부에서 404로 변환한다 (SC-09).
     */
    Optional<CustomerContact> findByIdAndCustomerId(UUID id, UUID customerId);

    /**
     * 현재 대표 담당자 (CU-11) — 대표를 교체할 때 옛 대표를 먼저 해제하는 데 쓴다.
     * {@code uk_customer_contact_primary}가 부분 유니크라 두 명이 겹치는 순간이 있으면 위반이 난다.
     */
    Optional<CustomerContact> findByCustomerIdAndIsPrimaryTrue(UUID customerId);

    /**
     * 담당자가 한 명이라도 있는지 — 첫 담당자를 대표로 저장할지 판정한다 (이슈 #107 설계 결정 1).
     * 개수는 쓰지 않으므로 count 대신 exists로 둔다.
     */
    boolean existsByCustomerId(UUID customerId);
}
