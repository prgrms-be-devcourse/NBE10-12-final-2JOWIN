package com.twojo.order.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.global.response.PageResponse;
import com.twojo.order.dto.OrderRequests;
import com.twojo.order.dto.OrderResponses;
import com.twojo.order.service.OrderService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 주문 (07 §C · OD).
 *
 * <p><b>전환 경로가 {@code /quotes/...}인데 이 컨트롤러에 있다.</b> 07이 정한 경로는
 * "승인된 견적을 주문으로 바꾼다"는 행위를 견적 자원 아래에 두지만, 그 결과로 만들어지는
 * 것은 주문이고 응답도 주문이다. 경로와 소유 모듈이 갈리는 자리라 여기 적어 둔다 —
 * 모듈 경계는 URL이 아니라 <b>무엇을 만드는가</b>로 정한다.
 *
 * <p>{@link AccessContext}는 인증 필터가 심은 principal에서 타입으로 주입된다 —
 * 요청에 회사·구성원 식별자가 실리지 않는다. 범위 판정(SC-01·04)은 서비스가 한다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** 목록 기본 정렬은 엔드포인트가 고정한다 — 클라이언트가 지정하지 않는다 (Q-39) */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final OrderService orderService;

    /**
     * 주문 전환 (OD-01~07, Q-25) — 승인 견적 하나가 주문 하나가 된다.
     *
     * <p>부수 효과로 <b>Deal이 성사(WON)</b>가 된다 (OD-06) — 응답의 {@code dealStage}가 그 결과다.
     * 승인되지 않았으면 409 {@code QUOTE_NOT_APPROVED}, 이미 전환됐으면 409
     * {@code QUOTE_ALREADY_CONVERTED}.
     */
    @PostMapping("/quotes/{quoteId}/convert-to-order")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponses.OrderDetail convert(AccessContext ctx, @PathVariable UUID quoteId) {
        return orderService.convert(ctx, quoteId);
    }

    /**
     * 목록 (OD-08) — {@code from}·{@code to}는 <b>전환일</b> 기준이고 양끝을 포함한다.
     * 영업은 담당 Deal의 주문만 본다 (SC-04).
     */
    @GetMapping("/orders")
    public PageResponse<OrderResponses.OrderRow> list(
            AccessContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return orderService.list(ctx, from, to, pageable(page, size));
    }

    /** 상세 (OD-09) — 스냅샷 항목 포함. {@code dealId}는 견적을 거쳐 붙는다 */
    @GetMapping("/orders/{orderId}")
    public OrderResponses.OrderDetail get(AccessContext ctx, @PathVariable UUID orderId) {
        return orderService.get(ctx, orderId);
    }

    /**
     * 착수일·납기 기록 (OD-10).
     * <p><b>두 날짜를 함께 덮어쓴다</b> — null은 "미변경"이 아니라 "지움"이다 ({@code Order.updateSchedule}).
     */
    @PatchMapping("/orders/{orderId}/schedule")
    public OrderResponses.OrderDetail updateSchedule(AccessContext ctx, @PathVariable UUID orderId,
                                                     @Valid @RequestBody OrderRequests.UpdateSchedule request) {
        return orderService.updateSchedule(ctx, orderId, request);
    }

    /**
     * Q-39 — 음수 페이지·과대 size를 그대로 넘기면 500이 된다. 여기서 잘라낸다.
     * <p>{@code size}가 0 이하면 기본값으로 돌린다 — {@code clamp}로 1을 만들면
     * "한 건짜리 페이지"라는 뜻이 되어, 값을 비운 요청의 의도와 다르다.
     */
    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, DEFAULT_SORT);
    }
}
