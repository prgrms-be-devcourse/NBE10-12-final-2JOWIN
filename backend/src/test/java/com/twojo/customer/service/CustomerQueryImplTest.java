package com.twojo.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.Role;
import com.twojo.customer.entity.Customer;
import com.twojo.customer.entity.CustomerContact;
import com.twojo.customer.repository.CustomerContactRepository;
import com.twojo.customer.repository.CustomerRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 경계 계약의 매핑·예외 변환 검증.
 *
 * <p>회사 스코프(SC-01)의 실질 검증은 {@code AccessContext} 주입 필터와 컨트롤러가 붙은 뒤
 * "타사 리소스를 요청하면 404가 오는가"로 한다 — 고객사·상품 API 이슈에서.
 */
@ExtendWith(MockitoExtension.class)
class CustomerQueryImplTest {

    private static final AccessContext CTX =
            new AccessContext(UUID.randomUUID(), UUID.randomUUID(), Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerContactRepository customerContactRepository;
    @InjectMocks private CustomerQueryImpl customerQuery;

    /** {@code willReturn(...)} 인자 안에서 부르면 중첩 스터빙이 된다 — 먼저 지역 변수로 만든다. */
    private static Customer customer(UUID id, String name) {
        Customer customer = mock(Customer.class);
        given(customer.getId()).willReturn(id);
        given(customer.getName()).willReturn(name);
        return customer;
    }

    @Test
    @DisplayName("고객사를 찾으면 id·이름만 담은 요약을 돌려준다")
    void get_returnsSummary() {
        UUID customerId = UUID.randomUUID();
        Customer customer = mock(Customer.class);
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

    /**
     * 판정 자체는 리포지토리에 위임하므로 이 테스트가 지키는 건 <b>인자 순서</b>다.
     * 두 인자가 모두 {@code UUID}라 뒤바꿔 써도 컴파일된다 — strict stub이 그 실수를 잡는다.
     * 계약과 리포지토리의 파라미터 순서를 {@code (customerId, contactId)}로 맞춰뒀으므로
     * 호출부에서 뒤집을 일 자체가 없다.
     */
    @Test
    @DisplayName("담당자 소속 판정을 리포지토리에 위임한다 — 인자 순서가 뒤바뀌면 실패")
    void existsContactInCustomer_delegatesInCorrectArgumentOrder() {
        UUID customerId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        given(customerContactRepository.existsByCustomerIdAndId(customerId, contactId)).willReturn(false);

        assertThat(customerQuery.existsContactInCustomer(customerId, contactId)).isFalse();
    }

    @Test
    @DisplayName("담당자를 찾으면 이름·직책·이메일을 돌려준다")
    void getContact_returnsSummary() {
        UUID contactId = UUID.randomUUID();
        CustomerContact contact = mock(CustomerContact.class);
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
    @DisplayName("빈 id 묶음이면 조회하지 않고 빈 목록을 돌려준다")
    void namesByIds_empty_doesNotQuery() {
        assertThat(customerQuery.namesByIds(CTX.companyId(), List.of())).isEmpty();

        then(customerRepository).should(never())
                .findByCompanyIdAndIdInAndDeletedAtIsNull(any(), any());
    }

    /**
     * 요청한 id 중 조회되지 않은 것은 <b>예외 없이 빠진다</b> — {@code get}이 같은 상황에서 404를
     * 던지는 것과 갈리는 지점이라 두 축을 한 케이스에서 함께 본다.
     * 회사 스코프·소프트 삭제 필터 자체는 파생 쿼리 이름이 보증하며 여기서는 실행되지 않는다.
     */
    @Test
    @DisplayName("조회된 것만 id·이름 요약으로 돌려준다 — 빠진 id에 예외를 던지지 않는다")
    void namesByIds_returnsFoundOnly_withoutException() {
        UUID alive = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        List<Customer> found = List.of(customer(alive, "도담건설"));
        given(customerRepository.findByCompanyIdAndIdInAndDeletedAtIsNull(
                CTX.companyId(), List.of(alive, missing)))
                .willReturn(found);

        var summaries = customerQuery.namesByIds(CTX.companyId(), List.of(alive, missing));

        assertThat(summaries)
                .extracting(CustomerQuery.CustomerSummary::id, CustomerQuery.CustomerSummary::name)
                .containsExactly(tuple(alive, "도담건설"));
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
