package com.twojo.quote.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.global.response.PageResponse;
import com.twojo.quote.dto.QuoteRequests;
import com.twojo.quote.dto.QuoteResponses;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.service.QuoteService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 견적 (07 §C · QT).
 *
 * <p>{@link AccessContext}는 인증 필터가 심은 principal에서 타입으로 주입된다 —
 * 요청에 회사·구성원 식별자가 실리지 않는다. 범위 판정(SC-01·02)은 서비스가 한다.
 *
 * <p>발송·회수·복제·미리보기·주문 전환은 이 컨트롤러에 없다 — 별도 이슈다.
 */
@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
public class QuoteController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** 목록 기본 정렬은 엔드포인트가 고정한다 — 클라이언트가 지정하지 않는다 (Q-39) */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final QuoteService quoteService;

    /** 작성 시작 (QT-01) — 빈 DRAFT + 채번. 종결 Deal이면 409 QUOTE_DEAL_CLOSED */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteResponses.QuoteDetail create(AccessContext ctx,
                                             @Valid @RequestBody QuoteRequests.CreateQuote request) {
        return quoteService.create(ctx, request);
    }

    /**
     * 목록·상태 조회 (QT-20).
     *
     * <p>{@code dealId}를 주면 그 Deal의 견적만 — 하나의 Deal에 여러 건이 있을 수 있다 (QT-18).
     * 영업은 어떤 필터를 넣든 담당 Deal의 견적만 본다 (SC-02).
     */
    @GetMapping
    public PageResponse<QuoteResponses.QuoteItemRow> list(
            AccessContext ctx,
            @RequestParam(required = false) Quote.Status status,
            @RequestParam(required = false) UUID dealId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return quoteService.list(ctx, status, dealId, pageable(page, size));
    }

    /** 상세 — 항목 포함 (sortOrder 오름차순, QT-07) */
    @GetMapping("/{quoteId}")
    public QuoteResponses.QuoteDetail get(AccessContext ctx, @PathVariable UUID quoteId) {
        return quoteService.get(ctx, quoteId);
    }

    /**
     * 작성 중 전체 갱신 (QT-02~11·23) — <b>PUT이다.</b> 항목은 전부 교체되고,
     * {@code terms}에 null이 오면 조건 문구가 지워진다. DRAFT가 아니면 409 QUOTE_NOT_DRAFT.
     */
    @PutMapping("/{quoteId}")
    public QuoteResponses.QuoteDetail update(AccessContext ctx, @PathVariable UUID quoteId,
                                             @Valid @RequestBody QuoteRequests.UpdateQuote request) {
        return quoteService.update(ctx, quoteId, request);
    }

    /** Q-39 — 음수 페이지·과대 size를 그대로 넘기면 500이 된다. 여기서 잘라낸다 */
    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize == 0 ? DEFAULT_PAGE_SIZE : safeSize, DEFAULT_SORT);
    }
}
