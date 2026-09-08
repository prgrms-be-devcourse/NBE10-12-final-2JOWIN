package com.twojo.onboarding.controller;

import com.twojo.global.response.PageResponse;
import com.twojo.onboarding.dto.ApplicationResponse;
import com.twojo.onboarding.dto.RejectApplicationRequest;
import com.twojo.onboarding.entity.Application;
import com.twojo.onboarding.service.ApplicationAdminService;
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
 * 가입 신청 심사 (07 §A · ON-03~07·14) — 플랫폼 관리자 전용.
 *
 * <p>{@code AccessContext}를 받지 않는다. 이 체인의 principal은 관리자 id 하나이고,
 * 신청은 회사에 속하지 않아 테넌트 스코프가 없다.
 */
@RestController
@RequestMapping("/admin/api/v1/applications")
@RequiredArgsConstructor
public class AdminApplicationController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int MAX_PAGE_SIZE = 100;

    /** 심사 대기열이라 오래된 것이 위다 — 먼저 온 신청을 먼저 처리한다. */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

    private final ApplicationAdminService applicationAdminService;

    /**
     * 목록 (ON-03) — status를 비우면 처리된 신청도 함께 나온다.
     *
     * <p>enum으로 바로 받는다. 없는 상태 값은 {@code GlobalExceptionHandler}의 타입 불일치
     * 처리가 400 {@code VALIDATION_FAILED} + fieldErrors로 바꾼다 — 빈 목록으로 답하면
     * 오타를 "해당 신청 없음"으로 읽게 된다. 손으로 파싱하던 것을 한 곳으로 모았다.
     *
     * <p>파라미터를 아예 안 보낸 것과 빈 문자열은 둘 다 null이다 — 그쪽은 전체 조회다.
     */
    @GetMapping
    public PageResponse<ApplicationResponse> list(
            @RequestParam(required = false) Application.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applicationAdminService.list(status, pageable(page, size));
    }

    /** 상세 */
    @GetMapping("/{applicationId}")
    public ApplicationResponse get(@PathVariable UUID applicationId) {
        return applicationAdminService.get(applicationId);
    }

    /** 승인 (ON-04·06·07) — 회사 생성 + 기업 관리자 계정 + 설정 링크 메일. */
    @PostMapping("/{applicationId}/approve")
    public ApplicationResponse approve(@PathVariable UUID applicationId) {
        return applicationAdminService.approve(applicationId);
    }

    /** 반려 (ON-05·14) — 사유 필수. */
    @PostMapping("/{applicationId}/reject")
    public ApplicationResponse reject(@PathVariable UUID applicationId,
                                      @Valid @RequestBody RejectApplicationRequest request) {
        return applicationAdminService.reject(applicationId, request);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
    }
}
