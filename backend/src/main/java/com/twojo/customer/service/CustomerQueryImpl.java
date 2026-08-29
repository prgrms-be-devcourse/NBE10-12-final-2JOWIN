package com.twojo.customer.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.CustomerQuery;
import com.twojo.customer.repository.CustomerContactRepository;
import com.twojo.customer.repository.CustomerRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CustomerQuery} 구현 — 타 도메인이 고객사·담당자 정보를 얻는 유일한 통로다 (docs/11 §7.2).
 *
 * <p>고객사는 회사 공유 자원이라 담당 개념이 없다 — {@code created_by_member_id}는 생성자 기록일 뿐
 * 접근 판정에 쓰지 않는다 (SC-03). 회사 스코프(SC-01)와 소프트 삭제만 조건으로 건다.
 *
 * <p>범위를 벗어난 조회는 존재 여부를 구별하지 않고 404로 응답한다 (SC-09).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CustomerQueryImpl implements CustomerQuery {

    private final CustomerRepository customerRepository;
    private final CustomerContactRepository customerContactRepository;

    @Override
    public CustomerSummary get(AccessContext ctx, UUID customerId) {
        return customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(customerId, ctx.companyId())
                .map(customer -> new CustomerSummary(customer.getId(), customer.getName()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Override
    public boolean existsContactInCustomer(UUID customerId, UUID contactId) {
        return customerContactRepository.existsByCustomerIdAndId(customerId, contactId);
    }

    /**
     * 담당자 정보 조회.
     *
     * <p><b>전제: 호출자가 {@link #existsContactInCustomer}로 소속을 이미 확인했다.</b>
     * 이 메서드에는 회사 검사가 없다 — {@code customer_contact}에 {@code company_id} 컬럼이 없어
     * (부모 경유 격리, docs/06) {@code contactId}만으로는 테넌트 판정이 불가능하기 때문이다.
     * 검증을 마친 뒤의 후속 조회로만 쓰고, <b>사용자 입력이 그대로 들어오는 경로에서 직접 호출하지 않는다</b>.
     */
    @Override
    public ContactSummary getContact(UUID contactId) {
        return customerContactRepository.findById(contactId)
                .map(contact -> new ContactSummary(
                        contact.getId(), contact.getName(), contact.getTitle(), contact.getEmail()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
