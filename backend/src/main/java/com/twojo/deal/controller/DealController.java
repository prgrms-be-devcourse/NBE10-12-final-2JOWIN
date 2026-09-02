package com.twojo.deal.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.deal.dto.DealRequests;
import com.twojo.deal.dto.DealResponses;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.service.DealService;
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
 * Deal (07 §C · DL).
 *
 * <p>{@link AccessContext}는 인증 필터가 심은 principal에서 타입으로 주입된다 (PR #30) —
 * 요청에 회사·구성원 식별자가 실리지 않는다. 범위 판정(SC-01·02·05)은 서비스가 한다.
 *
 * <p>단계 전이(advance·revert·lose·reopen)와 삭제는 이 컨트롤러에 없다 — 별도 이슈다.
 */
@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
public class DealController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** 목록 기본 정렬은 엔드포인트가 고정한다 — 클라이언트가 지정하지 않는다 (Q-39) */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final DealService dealService;

    /** 생성 (DL-01~04) — 회사의 모든 고객사에 가능, 배정 대상은 활성 구성원 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DealResponses.DealItem create(AccessContext ctx,
                                         @Valid @RequestBody DealRequests.CreateDeal request) {
        return dealService.create(ctx, request);
    }

    /**
     * 목록·보드 (DL-06·13·14).
     *
     * <p>보드는 단계별로 나눠 호출한다 — {@code stage}를 비우면 전 단계가 한 페이지에 섞인다.
     * 영업은 {@code assigneeId}를 무엇으로 넣든 본인 담당만 본다 (SC-02).
     */
    @GetMapping
    public PageResponse<DealResponses.DealItem> list(
            AccessContext ctx,
            @RequestParam(required = false) Deal.Stage stage,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return dealService.list(ctx, stage, assigneeId, customerId, pageable(page, size));
    }

    /** 상세 (DL-15·18) — 견적·주문은 요약만, 활동 이력 전체는 담지 않는다 */
    @GetMapping("/{dealId}")
    public DealResponses.DealDetail get(AccessContext ctx, @PathVariable UUID dealId) {
        return dealService.get(ctx, dealId);
    }

    /** 제목·예상 금액·마감일 수정 (DL-02·03) — null 필드는 변경하지 않는다 */
    @PatchMapping("/{dealId}")
    public DealResponses.DealItem update(AccessContext ctx, @PathVariable UUID dealId,
                                         @Valid @RequestBody DealRequests.UpdateDeal request) {
        return dealService.update(ctx, dealId, request);
    }

    /** 담당자 변경 (DL-05, SC-06) — 기업 관리자 전용, 역할 판정은 서비스가 한다 */
    @PatchMapping("/{dealId}/assignee")
    public DealResponses.DealItem changeAssignee(AccessContext ctx, @PathVariable UUID dealId,
                                                 @Valid @RequestBody DealRequests.ChangeAssignee request) {
        return dealService.changeAssignee(ctx, dealId, request);
    }

    /**
     * 페이지 요청 조립 — 음수 페이지와 과대 size를 여기서 잘라낸다.
     * 정렬을 인자로 받지 않는 것이 핵심이다 (Q-39 "정렬은 엔드포인트별 기본값 고정").
     */
    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize == 0 ? DEFAULT_PAGE_SIZE : safeSize, DEFAULT_SORT);
    }
}
