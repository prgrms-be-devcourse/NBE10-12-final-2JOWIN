package com.twojo.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.customer.entity.Customer;
import com.twojo.customer.entity.CustomerContact;
import com.twojo.customer.repository.CustomerContactRepository;
import com.twojo.customer.repository.CustomerRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 경계 계약의 매핑·예외 변환 검증. 회사 스코프 조건이 SQL로 실제 걸리는지는
 * {@link com.twojo.TenantScopeIsolationTest}가 실제 DB로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CustomerQueryImplTest {

    private static final AccessContext CTX =
            new AccessContext(UUID.randomUUID(), UUID.randomUUID(), Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerContactRepository customerContactRepository;
    @InjectMocks private CustomerQueryImpl customerQuery;

    @Test
    @DisplayName("고객사를 찾으면 id·이름만 담은 요약을 돌려준다")
    void get_returnsSummary() {
        UUID customerId = UUID.randomUUID();
        Customer customer = org.mockito.Mockito.mock(Customer.class);
        given(customer.getId()).willReturn(customerId);
        given(customer.getName()).willReturn("도담건설");
        given(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(customerId, CTX.companyId()))
                .willReturn(Optional.of(customer));

        var summary = customerQuery.get(CTX, customerId);

        assertThat(summary.id()).isEqualTo(customerId);
        assertThat(summary.name()).isEqualTo("도담건설");
    }

    @Test
    @DisplayName("범위 밖 고객사는 403이 아니라 404 — 존재 여부를 구별하지 않는다 (SC-09)")
    void get_outOfScope_throwsNotFound() {
        UUID customerId = UUID.randomUUID();
        given(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(customerId, CTX.companyId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> customerQuery.get(CTX, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 고객사에 속한 담당자면 false — 견적 수신인 검증의 재료 (CONTACT_NOT_IN_CUSTOMER)")
    void existsContactInCustomer_otherCustomer_false() {
        UUID customerId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        given(customerContactRepository.existsByIdAndCustomerId(contactId, customerId)).willReturn(false);

        assertThat(customerQuery.existsContactInCustomer(customerId, contactId)).isFalse();
    }

    @Test
    @DisplayName("담당자를 찾으면 이름·직책·이메일을 돌려준다")
    void getContact_returnsSummary() {
        UUID contactId = UUID.randomUUID();
        CustomerContact contact = org.mockito.Mockito.mock(CustomerContact.class);
        given(contact.getId()).willReturn(contactId);
        given(contact.getName()).willReturn("이수정");
        given(contact.getTitle()).willReturn("총무팀 대리");
        given(contact.getEmail()).willReturn("sujeong@dodam.co.kr");
        given(customerContactRepository.findById(contactId)).willReturn(Optional.of(contact));

        var summary = customerQuery.getContact(contactId);

        assertThat(summary.name()).isEqualTo("이수정");
        assertThat(summary.title()).isEqualTo("총무팀 대리");
        assertThat(summary.email()).isEqualTo("sujeong@dodam.co.kr");
    }

    @Test
    @DisplayName("없는 담당자는 404")
    void getContact_missing_throwsNotFound() {
        UUID contactId = UUID.randomUUID();
        given(customerContactRepository.findById(contactId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> customerQuery.getContact(contactId))
                .isInstanceOf(BusinessException.class);
    }
}
