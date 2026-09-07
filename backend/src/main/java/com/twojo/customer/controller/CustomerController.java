package com.twojo.customer.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.customer.dto.ContactResponse;
import com.twojo.customer.dto.CreateContactRequest;
import com.twojo.customer.dto.CreateCustomerRequest;
import com.twojo.customer.dto.CustomerDetailResponse;
import com.twojo.customer.dto.CustomerResponse;
import com.twojo.customer.dto.UpdateContactRequest;
import com.twojo.customer.dto.UpdateCustomerRequest;
import com.twojo.customer.service.CustomerService;
import com.twojo.global.response.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객사·담당자 (07 §B · CU).
 *
 * <p>{@link AccessContext}는 인증 필터가 심은 principal에서 타입으로 주입된다 (PR #30).
 * 고객사는 회사 공유 자원이라 역할 분기가 없다 — 전 구성원이 조회하고 수정한다 (SC-03).

 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int MAX_PAGE_SIZE = 100;

    /** 최근 등록이 위다 — 카탈로그의 이름순과 다르다 (Q-39, 엔드포인트별 고정). */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final CustomerService customerService;

    /** 목록·검색 (CU-03·04) — 회사 전체. {@code keyword}는 이름 부분 일치다 */
    @GetMapping
    public PageResponse<CustomerResponse> list(
            AccessContext ctx,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String industry,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return customerService.list(ctx, keyword, industry, pageable(page, size));
    }

    /** 등록 (CU-01·02) — 등록자는 서버가 채운다. 요청 바디에 없다 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(AccessContext ctx, @Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(ctx, request);
    }

    /** 상세 (CU-05·12) — 담당자 목록과 Deal 이력을 함께 싣는다 */
    @GetMapping("/{customerId}")
    public CustomerDetailResponse get(AccessContext ctx, @PathVariable UUID customerId) {
        return customerService.get(ctx, customerId);
    }

    /** 수정 (CU-06) — null 필드는 미변경 (08 §B) */
    @PatchMapping("/{customerId}")
    public CustomerResponse update(AccessContext ctx, @PathVariable UUID customerId,
                                   @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(ctx, customerId, request);
    }

    /** 담당자 추가 (CU-09·10) — 그 고객사의 첫 담당자면 대표가 된다 (이슈 #107 설계 결정 1) */
    @PostMapping("/{customerId}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse addContact(AccessContext ctx, @PathVariable UUID customerId,
                                      @Valid @RequestBody CreateContactRequest request) {
        return customerService.addContact(ctx, customerId, request);
    }

    /** 담당자 수정 — null 필드는 미변경. 대표 여부는 이 경로로 바뀌지 않는다 (08 §B) */
    @PatchMapping("/{customerId}/contacts/{contactId}")
    public ContactResponse updateContact(AccessContext ctx, @PathVariable UUID customerId,
                                         @PathVariable UUID contactId,
                                         @Valid @RequestBody UpdateContactRequest request) {
        return customerService.updateContact(ctx, customerId, contactId, request);
    }

    /**
     * 대표 담당자 지정 (CU-11) — 본문 없음. 기존 대표는 자동 해제된다.
     * 바뀐 담당자를 돌려줘 프론트가 재조회하지 않게 한다.
     */
    @PostMapping("/{customerId}/contacts/{contactId}/set-primary")
    public ContactResponse setPrimaryContact(AccessContext ctx, @PathVariable UUID customerId,
                                             @PathVariable UUID contactId) {
        return customerService.setPrimaryContact(ctx, customerId, contactId);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
    }
}
