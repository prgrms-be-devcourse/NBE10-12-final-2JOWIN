package com.twojo.quote.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.PublicQuoteResponse;
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
 * <p>발송·회수는 이 컨트롤러에 없다 — 별도 이슈다. 복제(QT-19)는 여기 있다.
 * <b>주문 전환({@code POST /quotes/{id}/convert-to-order})은 경로만 여기 아래에 있고
 * {@code OrderController}가 받는다</b> — 만들어지는 것이 주문이기 때문이다 (#160).
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
    /** 복제 (QT-19) — 원본을 새 DRAFT로 베낀다. 종결 Deal이면 409 (Q-25) */
    @PostMapping("/{quoteId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteResponses.QuoteDetail clone(AccessContext ctx, @PathVariable UUID quoteId) {
        return quoteService.clone(ctx, quoteId);
    }

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

    /**
     * 발송 (QT-13~16, AP-01) — 열람 링크 발급 + 안내 메일 예약이 <b>같은 트랜잭션</b>에서 일어난다.
     *
     * <p>응답에 <b>자동 승급이 반영된 Deal 단계</b>가 실린다 (Q-25) — 화면이 딜을 다시 묻지 않아도 된다.
     */
    @PostMapping("/{quoteId}/send")
    public QuoteResponses.SendResult send(AccessContext ctx, @PathVariable UUID quoteId,
                                          @Valid @RequestBody QuoteRequests.SendQuote request) {
        return quoteService.send(ctx, quoteId, request);
    }

    /** 회수 (QT-17) — 링크 즉시 만료. <b>종결 Deal에서도 된다</b> (정리 목적) */
    @PostMapping("/{quoteId}/withdraw")
    public QuoteResponses.QuoteDetail withdraw(AccessContext ctx, @PathVariable UUID quoteId) {
        return quoteService.withdraw(ctx, quoteId);
    }

    /**
     * 수신인 변경 재발송 (AP-13) — 기존 링크를 닫고 새로 발급한다. <b>견적 상태는 그대로다.</b>
     * 바뀌는 것이 링크뿐이라 본문 없이 204로 답한다.
     */
    @PostMapping("/{quoteId}/view-token/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendViewToken(AccessContext ctx, @PathVariable UUID quoteId,
                                @Valid @RequestBody QuoteRequests.ResendViewToken request) {
        quoteService.resendViewToken(ctx, quoteId, request);
    }

    /** 열람 링크 수동 만료 (AP-14) — 링크만 닫는다. 멱등이라 두 번 눌러도 안전하다 */
    @PostMapping("/{quoteId}/view-token/expire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void expireViewToken(AccessContext ctx, @PathVariable UUID quoteId) {
        quoteService.expireViewToken(ctx, quoteId);
    }

    /**
     * 발송 전 미리보기 (QT-12) — 고객 열람 페이지와 <b>같은 응답</b>을 돌려준다.
     *
     * <p>응답이 {@code PublicQuoteResponse}인 것은 08 §C의 규약이다 — 미리보기가 고객 화면보다
     * 적게 보여주면 확인해 주는 것이 없어진다. 내부 식별자({@code dealId}·{@code companyId})는
     * 이 모양에 실리지 않는다.
     */
    @GetMapping("/{quoteId}/preview")
    public PublicQuoteResponse preview(AccessContext ctx, @PathVariable UUID quoteId) {
        return quoteService.preview(ctx, quoteId);
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
