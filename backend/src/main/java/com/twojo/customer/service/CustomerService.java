package com.twojo.customer.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.MemberQuery;
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
import com.twojo.global.response.PageResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객사·담당자 (CU-01~06·09~12) — 회사 공유 자원이라 담당 개념이 없다.
 * 조회도 수정도 전 구성원이 한다 (SC-03) — 상품과 달리 역할 검사가 없는 이유다.
 *
 * <p>타사·미존재 리소스는 둘 다 404다. 있는지 없는지를 구별해서 알려주지 않는다 (SC-09).
 *
 * <p><b>담당자는 부모 고객사를 거쳐서만 닿는다</b> — {@code customer_contact}에 {@code company_id}가
 * 없어(06 부모 경유 격리) 담당자 단독 조회로는 회사를 판정할 수 없다. 모든 담당자 메서드가
 * 먼저 고객사를 회사 스코프로 찾는 것이 테넌트 방어 그 자체다.
 *
 * <p>삭제(CU-07·08·14)는 여기에 없다 — 차단 규칙이 C·D 경계 조회를 필요로 해 별도 이슈다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerContactRepository contactRepository;
    private final MemberQuery memberQuery;
    private final DealQuery dealQuery;

    /**
     * 목록·검색 (CU-03·04) — 회사 전체가 나온다. 영업 담당자도 같은 결과를 본다 (SC-03).
     * {@code keyword}·{@code industry}는 없으면 조건에서 빠진다.
     */
    public PageResponse<CustomerResponse> list(AccessContext ctx, String keyword, String industry,
                                               Pageable pageable) {
        return PageResponse.from(
                customerRepository.search(ctx.companyId(), blankToNull(keyword), blankToNull(industry), pageable)
                        .map(CustomerResponse::of));
    }

    /** 등록 (CU-01·02) — 등록자는 요청 바디가 아니라 AccessContext에서 온다. */
    @Transactional
    public CustomerResponse create(AccessContext ctx, CreateCustomerRequest request) {
        Customer customer = Customer.create(ctx.companyId(), ctx.memberId(), request.name(),
                request.industry(), request.size(), request.note());
        return CustomerResponse.of(customerRepository.save(customer));
    }

    /**
     * 상세 (CU-05·12) — 담당자 목록과 Deal 이력을 함께 싣는다.
     *
     * <p>Deal 이력은 C의 {@code summariesByCustomer}로 받는다. 이 시점에 고객사가 회사 스코프로
     * 확인됐으므로 그 딜들도 같은 회사 것이다.
     */
    public CustomerDetailResponse get(AccessContext ctx, UUID customerId) {
        Customer customer = findInScope(ctx, customerId);
        List<ContactResponse> contacts = contactRepository
                .findByCustomerIdOrderByIsPrimaryDescNameAsc(customerId)
                .stream().map(ContactResponse::of).toList();
        List<DealQuery.DealSummary> deals = dealQuery.summariesByCustomer(customerId);

        return CustomerDetailResponse.of(customer, memberQuery.get(customer.getCreatedByMemberId()).name(),
                contacts, deals);
    }

    /** 수정 (CU-06) — null로 온 필드는 미변경이다 (08 §B). */
    @Transactional
    public CustomerResponse update(AccessContext ctx, UUID customerId, UpdateCustomerRequest request) {
        Customer customer = findInScope(ctx, customerId);
        customer.update(request.name(), request.industry(), request.size(), request.note());
        return CustomerResponse.of(customer);
    }

    /**
     * 담당자 추가 (CU-09·10).
     *
     * <p><b>그 고객사의 첫 담당자면 대표가 된다</b> (이슈 #107 설계 결정 1). 정본 문서에 최초 지정
     * 규칙이 없어 여기서 정했다 — 자동 지정이 없으면 대표 0명인 고객사가 정상 상태가 되어
     * {@code PRIMARY_CONTACT_REQUIRED}(대표 0명 방지)가 전제를 잃는다.
     */
    @Transactional
    public ContactResponse addContact(AccessContext ctx, UUID customerId, CreateContactRequest request) {
        findInScope(ctx, customerId);

        CustomerContact contact = CustomerContact.create(customerId, request.name(), request.title(),
                request.phone(), request.email());
        if (!contactRepository.existsByCustomerId(customerId)) {
            contact.markPrimary();
        }
        return ContactResponse.of(contactRepository.save(contact));
    }

    /** 담당자 수정 — null로 온 필드는 미변경이다. 대표 여부는 이 경로로 바뀌지 않는다 (08 §B). */
    @Transactional
    public ContactResponse updateContact(AccessContext ctx, UUID customerId, UUID contactId,
                                         UpdateContactRequest request) {
        CustomerContact contact = findContactInScope(ctx, customerId, contactId);
        contact.update(request.name(), request.title(), request.phone(), request.email());
        return ContactResponse.of(contact);
    }

    /**
     * 대표 담당자 지정 (CU-11) — 기존 대표는 자동 해제된다. 대표는 항상 1명이다.
     *
     * <p><b>해제를 먼저 flush한다.</b> {@code uk_customer_contact_primary}가 부분 유니크라
     * 두 행이 동시에 대표인 순간이 있으면 위반이 난다. JPA는 dirty checking 순서를 보장하지
     * 않으므로 순서를 여기서 고정한다.
     *
     * <p>이미 대표인 담당자를 다시 지정하면 무동작이다 — 던질 에러 코드가 07 부록에 없고,
     * 같은 결과를 요청한 것이라 실패시킬 이유가 없다.
     */
    @Transactional
    public ContactResponse setPrimaryContact(AccessContext ctx, UUID customerId, UUID contactId) {
        CustomerContact target = findContactInScope(ctx, customerId, contactId);
        if (target.isPrimary()) {
            return ContactResponse.of(target);
        }

        contactRepository.findByCustomerIdAndIsPrimaryTrue(customerId).ifPresent(previous -> {
            previous.releasePrimary();
            contactRepository.saveAndFlush(previous);
        });
        target.markPrimary();
        return ContactResponse.of(target);
    }

    /** 회사 스코프 조회. 없거나 타사 것이면 404 — 존재 여부를 구별해서 말하지 않는다 (SC-09). */
    private Customer findInScope(AccessContext ctx, UUID customerId) {
        return customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(customerId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 담당자 조회 — 부모 고객사를 회사 스코프로 먼저 확인한 뒤, 그 고객사 소속인지 본다.
     * 타사 담당자든 다른 고객사 담당자든 결과는 같은 404다.
     */
    private CustomerContact findContactInScope(AccessContext ctx, UUID customerId, UUID contactId) {
        findInScope(ctx, customerId);
        return contactRepository.findByIdAndCustomerId(contactId, customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 빈 검색어는 필터가 아니다 — {@code ?keyword=}로 온 빈 문자열을 조건에서 뺀다. */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
