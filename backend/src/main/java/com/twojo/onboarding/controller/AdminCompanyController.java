package com.twojo.onboarding.controller;

import com.twojo.global.response.PageResponse;
import com.twojo.onboarding.dto.CompanyResponse;
import com.twojo.onboarding.dto.SuspendCompanyRequest;
import com.twojo.onboarding.service.CompanyAdminService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회사 관리 (07 §A · ON-08·10·12) — 플랫폼 관리자 전용.
 *
 * <p>회사의 영업 데이터로 들어가는 경로는 없다 (ON-11). 이 체인은 {@code /api/v1/**}에
 * 닿지 않으므로 그 경계는 {@code SecurityConfig}가 이미 지킨다.
 */
@RestController
@RequestMapping("/admin/api/v1/companies")
@RequiredArgsConstructor
public class AdminCompanyController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int MAX_PAGE_SIZE = 100;

    /** 찾는 대상이라 이름순이다 — 구성원 목록(MB-07)과 같은 기준이다. */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final CompanyAdminService companyAdminService;

    /** 목록 · 이용 현황 (ON-12) — 정지된 회사도 함께 나온다. */
    @GetMapping
    public PageResponse<CompanyResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return companyAdminService.list(pageable(page, size));
    }

    /** 정지 (ON-08·09) — 사유 필수. 구성원 세션이 함께 끊긴다. */
    @PostMapping("/{companyId}/suspend")
    public CompanyResponse suspend(@PathVariable UUID companyId,
                                   @Valid @RequestBody SuspendCompanyRequest request) {
        return companyAdminService.suspend(companyId, request);
    }

    /** 정지 해제 (ON-10) — 구성원은 재로그인이 필요하다. */
    @PostMapping("/{companyId}/reactivate")
    public CompanyResponse reactivate(@PathVariable UUID companyId) {
        return companyAdminService.reactivate(companyId);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
    }
}
