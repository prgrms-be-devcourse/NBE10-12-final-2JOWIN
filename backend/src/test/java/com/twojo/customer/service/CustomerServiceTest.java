package com.twojo.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.customer.dto.ContactResponse;
import com.twojo.customer.dto.CreateContactRequest;
import com.twojo.customer.dto.CreateCustomerRequest;
import com.twojo.customer.dto.CustomerDetailResponse;
import com.twojo.customer.dto.CustomerResponse;
import com.twojo.customer.dto.UpdateContactRequest;
import com.twojo.customer.dto.UpdateCustomerRequest;
import com.twojo.customer.entity.Customer;
import com.twojo.customer.entity.CustomerContact;
import com.twojo.customer.repository.CustomerContactRepository;
import com.twojo.customer.repository.CustomerRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 고객사 서비스 — 회사 스코프(SC-01·09) · 부모 경유 담당자 접근(06) · 첫 담당자 자동 대표(#107 설계 결정 1)
 * · 대표 교체 순서(CU-11) · PATCH 부분 수정(08 §B).
 *
 * <p>역할 검사는 없다 — 고객사는 회사 공유 자원이라 전 구성원이 조회하고 수정한다 (SC-03).
 *
 * <p>부분 유니크(대표 1명)가 실제로 걸리는지는 목으로 재현되지 않는다. 여기서 지키는 것은
 * <b>해제를 먼저 flush한다</b>는 순서다 — 통합 테스트는 회사 행을 만들 수단이 없어 아직 못 짠다
 * (공유문서 §5, A의 공용 픽스처 대기).
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID CONTACT_ID = UUID.randomUUID();

    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerContactRepository contactRepository;
    @Mock private MemberQuery memberQuery;
    @Mock private DealQuery dealQuery;
    @InjectMocks private CustomerService customerService;

    private static Customer 고객사() {
        return Customer.create(COMPANY_ID, MEMBER_ID, "도담건설", "건설", "중소기업", "정기 거래처");
    }

    private static CustomerContact 담당자(String name) {
        return CustomerContact.create(CUSTOMER_ID, name, "총무팀 대리", "010-3000-0001", name + "@dodam.co.kr");
    }

    private void 고객사있음() {
        given(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(CUSTOMER_ID, COMPANY_ID))
                .willReturn(Optional.of(고객사()));
    }

    @Test
    @DisplayName("빈 검색어는 필터가 아니다 — ?keyword= 로 온 공백은 조건에서 빠진다")
    void list_blankKeyword_notFiltered() {
        given(customerRepository.search(eq(COMPANY_ID), isNull(), isNull(), any())).willReturn(Page.empty());

        customerService.list(SALES, "   ", "", PageRequest.of(0, 20));

        then(customerRepository).should().search(eq(COMPANY_ID), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("영업 담당자도 회사 전체 고객사를 본다 — 담당 축이 없다 (SC-03)")
    void list_salesRep_seesWholeCompany() {
        given(customerRepository.search(eq(COMPANY_ID), eq("도담"), isNull(), any())).willReturn(Page.empty());

        customerService.list(SALES, "도담", null, PageRequest.of(0, 20));

        then(customerRepository).should().search(eq(COMPANY_ID), eq("도담"), isNull(), any());
    }

    @Test
    @DisplayName("등록은 등록자를 AccessContext에서 채운다 — 요청 바디에 없다 (CU-01·02)")
    void create_fillsCreatedByFromContext() {
        given(customerRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.create(SALES,
                new CreateCustomerRequest("도담건설", "건설", "중소기업", "정기 거래처"));

        assertThat(response.name()).isEqualTo("도담건설");
        assertThat(response.createdByMemberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("타사 고객사를 조회하면 404 — 403이 아니다 (SC-01·09)")
    void get_otherCompany_notFound() {
        given(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(CUSTOMER_ID, COMPANY_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.get(SALES, CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("상세는 담당자 목록과 Deal 이력을 함께 싣는다 (CU-05·12)")
    void get_detail_includesContactsAndDeals() {
        고객사있음();
        given(contactRepository.findByCustomerIdOrderByIsPrimaryDescNameAsc(CUSTOMER_ID))
                .willReturn(List.of(담당자("이수정")));
        given(dealQuery.summariesByCustomer(CUSTOMER_ID)).willReturn(List.of(
                new DealQuery.DealSummary(UUID.randomUUID(), "본사 사옥 비품", "NEGOTIATION",
                        5_000_000L, null, Instant.now())));
        given(memberQuery.get(MEMBER_ID)).willReturn(new MemberQuery.MemberSummary(MEMBER_ID, "한상민", true));

        CustomerDetailResponse response = customerService.get(SALES, CUSTOMER_ID);

        assertThat(response.contacts()).hasSize(1);
        assertThat(response.deals()).singleElement()
                .extracting(CustomerDetailResponse.DealSummary::title).isEqualTo("본사 사옥 비품");
        assertThat(response.createdByMemberName()).isEqualTo("한상민");
    }

    @Test
    @DisplayName("수정에서 null로 온 필드는 바뀌지 않는다 (08 §B PATCH 규약)")
    void update_nullFields_unchanged() {
        Customer customer = 고객사();
        given(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(CUSTOMER_ID, COMPANY_ID))
                .willReturn(Optional.of(customer));

        customerService.update(SALES, CUSTOMER_ID, new UpdateCustomerRequest(null, "제조", null, null));

        assertThat(customer.getName()).isEqualTo("도담건설");
        assertThat(customer.getIndustry()).isEqualTo("제조");
        assertThat(customer.getSize()).isEqualTo("중소기업");
    }

    @Test
    @DisplayName("첫 담당자는 대표가 된다 (#107 설계 결정 1)")
    void addContact_first_becomesPrimary() {
        고객사있음();
        given(contactRepository.existsByCustomerId(CUSTOMER_ID)).willReturn(false);
        given(contactRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        ContactResponse response = customerService.addContact(SALES, CUSTOMER_ID,
                new CreateContactRequest("이수정", "총무팀 대리", "010-3000-0001", "sujeong@dodam.co.kr"));

        assertThat(response.primary()).isTrue();
    }

    @Test
    @DisplayName("두 번째 담당자는 대표가 아니다 — 대표는 set-primary로만 옮긴다")
    void addContact_second_notPrimary() {
        고객사있음();
        given(contactRepository.existsByCustomerId(CUSTOMER_ID)).willReturn(true);
        given(contactRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        ContactResponse response = customerService.addContact(SALES, CUSTOMER_ID,
                new CreateContactRequest("박건우", "구매팀 사원", null, "gunwoo@dodam.co.kr"));

        assertThat(response.primary()).isFalse();
    }

    @Test
    @DisplayName("다른 고객사의 담당자 id를 넣으면 404 — 부모 경유 격리 (06)")
    void updateContact_foreignContact_notFound() {
        고객사있음();
        given(contactRepository.findByIdAndCustomerId(CONTACT_ID, CUSTOMER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateContact(SALES, CUSTOMER_ID, CONTACT_ID,
                new UpdateContactRequest("이수정", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("담당자 조회는 부모 고객사를 먼저 회사 스코프로 확인한다 — 고객사가 타사면 담당자를 읽지 않는다")
    void updateContact_otherCompanyCustomer_stopsBeforeContactLookup() {
        given(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(CUSTOMER_ID, COMPANY_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateContact(SALES, CUSTOMER_ID, CONTACT_ID,
                new UpdateContactRequest("이수정", null, null, null)))
                .isInstanceOf(BusinessException.class);

        then(contactRepository).should(never()).findByIdAndCustomerId(any(), any());
    }

    @Test
    @DisplayName("대표 지정은 옛 대표를 먼저 해제하고 flush한다 — 부분 유니크 위반 방지 (CU-11)")
    void setPrimary_releasesPreviousFirst() {
        고객사있음();
        CustomerContact previous = 담당자("이수정");
        previous.markPrimary();
        CustomerContact target = 담당자("박건우");
        given(contactRepository.findByIdAndCustomerId(CONTACT_ID, CUSTOMER_ID)).willReturn(Optional.of(target));
        given(contactRepository.findByCustomerIdAndIsPrimaryTrue(CUSTOMER_ID)).willReturn(Optional.of(previous));

        ContactResponse response = customerService.setPrimaryContact(SALES, CUSTOMER_ID, CONTACT_ID);

        InOrder order = inOrder(contactRepository);
        order.verify(contactRepository).findByCustomerIdAndIsPrimaryTrue(CUSTOMER_ID);
        order.verify(contactRepository).saveAndFlush(previous);

        assertThat(previous.isPrimary()).isFalse();
        assertThat(response.primary()).isTrue();
    }

    @Test
    @DisplayName("이미 대표인 담당자를 다시 지정하면 무동작이다 — 던질 에러 코드가 07 부록에 없다")
    void setPrimary_alreadyPrimary_isIdempotent() {
        고객사있음();
        CustomerContact target = 담당자("이수정");
        target.markPrimary();
        given(contactRepository.findByIdAndCustomerId(CONTACT_ID, CUSTOMER_ID)).willReturn(Optional.of(target));

        ContactResponse response = customerService.setPrimaryContact(SALES, CUSTOMER_ID, CONTACT_ID);

        assertThat(response.primary()).isTrue();
        then(contactRepository).should(never()).findByCustomerIdAndIsPrimaryTrue(any());
        then(contactRepository).should(never()).saveAndFlush(any());
    }
}
