package com.twojo.quote.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.ProductQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.global.sequence.DocumentNumberService;
import com.twojo.global.sequence.DocumentSequence.DocType;
import com.twojo.quote.dto.QuoteRequests;
import com.twojo.quote.dto.QuoteResponses;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.entity.QuoteItem;
import com.twojo.quote.repository.QuoteRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 견적 작성·조회·수정 (QT-01~11·18·20·23·24).
 *
 * <p><b>범위 판정은 {@code ctx.scope()}만 읽는다</b> — Role→Scope 변환은 인증 필터가 이미 했다.
 * 다만 견적에는 담당자 컬럼이 없다: <b>범위가 Deal에서 파생한다</b> (SC-02, ERD 설계 원칙
 * "담당 기준은 deal.assignee_member_id 하나"). 그래서 영업의 목록은 담당 Deal id 집합으로 좁히고,
 * 단건은 그 Deal의 담당자와 대조한다.
 *
 * <p>타 도메인 참조는 전부 경계 인터페이스 경유다 (11 §7.3) — Deal은 {@link DealQuery},
 * 카탈로그는 {@link ProductQuery}. 범위 밖이거나 없는 대상은 <b>구별 없이 404</b>다 (SC-09).
 *
 * <p>발송·회수·복제·주문 전환은 이 클래스에 없다 — 별도 이슈다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteService {

    /**
     * 작성 시작 시점의 임시 유효기간 — 오늘로부터 30일 (KST).
     *
     * <p><b>문서에 근거가 없어 여기서 정했다.</b> 07·08의 {@code CreateQuoteRequest}는 dealId
     * 하나만 받는데 {@code quote.valid_until}은 {@code NOT NULL}이라, 작성 시작 단계에 값이
     * 반드시 있어야 한다. 담당자가 PUT에서 실제 기간을 지정하므로(QT-09) 이 값은 그때까지의
     * 자리표시자이고, 발송 전에는 항상 덮인다 — 08의 {@code UpdateQuoteRequest.validUntil}이
     * {@code @NotNull @Future}다.
     *
     * <p>대안은 컬럼을 nullable로 바꾸는 것인데 스키마 변경(마이그레이션 + ERD 버전 업)이
     * 딸려온다. PR 「리뷰어에게」에 올려 팀 판단을 받는다.
     */
    private static final int DEFAULT_VALIDITY_DAYS = 30;

    /** 유효기간은 사람이 읽는 날짜라 한국 날짜로 끊는다 — 채번의 연월 판정과 같은 이유 (#72) */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final QuoteRepository quoteRepository;
    private final DealQuery dealQuery;
    private final ProductQuery productQuery;
    private final DocumentNumberService documentNumberService;

    /**
     * 작성 시작 (QT-01) — 빈 DRAFT 하나를 만든다. 항목은 이어지는 PUT이 채운다.
     *
     * <p>Deal 하나에 견적을 여러 건 만들 수 있다 (QT-18) — 중복 검사를 하지 않는다.
     */
    @Transactional
    public QuoteResponses.QuoteDetail create(AccessContext ctx, QuoteRequests.CreateQuote request) {
        UUID dealId = request.dealId();
        requireDealInScope(ctx, dealId);

        // 종결(WON·LOST) Deal에는 견적을 붙이지 않는다 (Q-25). 없는 Deal도 false지만
        // 위에서 이미 404로 갈라졌으므로 여기 도달하면 "종결"이 유일한 원인이다.
        if (!dealQuery.isOpen(dealId)) {
            throw new BusinessException(ErrorCode.QUOTE_DEAL_CLOSED);
        }

        // 채번은 이 트랜잭션 안에서 일어난다 — DocumentNumberService가 MANDATORY라
        // 트랜잭션 밖 호출은 즉시 실패하고, 이 메서드가 롤백되면 번호도 되돌아간다 (#72).
        String quoteNo = documentNumberService.next(ctx.companyId(), DocType.QUOTE);

        LocalDate validUntil = LocalDate.now(SEOUL).plusDays(DEFAULT_VALIDITY_DAYS);
        Quote quote = quoteRepository.save(Quote.draft(ctx.companyId(), dealId, quoteNo, validUntil));
        return QuoteResponses.QuoteDetail.of(quote);
    }

    /**
     * 목록·상태 조회 (QT-20).
     *
     * <p>영업(OWNED_ONLY)은 <b>담당 Deal의 견적만</b> 본다 — 요청에 어떤 dealId를 넣든 그렇다.
     * 담당 Deal이 하나도 없으면 빈 목록이다({@code QuoteSpecs}가 빈 집합을 거짓 조건으로 만든다).
     * 기업 관리자는 회사 전체다 (SC-05).
     */
    public PageResponse<QuoteResponses.QuoteItemRow> list(AccessContext ctx, Quote.Status status,
                                                          UUID dealId, Pageable pageable) {
        Collection<UUID> visibleDealIds = ctx.scope() == AccessScope.OWNED_ONLY
                ? dealQuery.assignedDealIds(ctx.companyId(), ctx.memberId())
                : null;   // null = 제한 없음

        return PageResponse.from(quoteRepository
                .search(ctx.companyId(), status, dealId, visibleDealIds, pageable)
                .map(QuoteResponses.QuoteItemRow::of));
    }

    /** 상세 — 항목 포함 */
    public QuoteResponses.QuoteDetail get(AccessContext ctx, UUID quoteId) {
        return QuoteResponses.QuoteDetail.of(findInScope(ctx, quoteId));
    }

    /**
     * 발송 전 미리보기 (QT-12) — <b>고객이 보게 될 것과 같은 데이터</b>다.
     *
     * <p>{@code QuoteQueryImpl.toPublicView}를 그대로 쓴다. 여기서 따로 조립하면 미리보기와
     * 실제 열람 화면이 갈리는데, 그러면 미리보기가 확인해 주는 것이 아무것도 없어진다.
     * 다만 <b>범위 판정은 구성원 규칙</b>을 쓴다 — 부르는 쪽이 로그인한 구성원이기 때문이다.
     */
    public QuoteQuery.PublicQuoteView preview(AccessContext ctx, UUID quoteId) {
        return QuoteQueryImpl.toPublicView(findInScope(ctx, quoteId));
    }

    /**
     * 작성 중 전체 갱신 (QT-02~11·23) — 항목은 전부 교체된다.
     *
     * <p>순서가 중요하다: <b>version 확인 → 본문 → 항목</b>. 항목 조립이 카탈로그를 조회하므로
     * (실패할 수 있다) 낡은 요청을 먼저 걸러야 헛일을 하지 않는다.
     */
    @Transactional
    public QuoteResponses.QuoteDetail update(AccessContext ctx, UUID quoteId,
                                             QuoteRequests.UpdateQuote request) {
        Quote quote = findInScope(ctx, quoteId);
        quote.checkVersion(request.version());

        quote.update(request.validUntil(), parseVatMode(request.vatMode()), request.terms());
        quote.replaceItems(request.items().stream().map(line -> toItem(ctx, line)).toList());

        return QuoteResponses.QuoteDetail.of(quote);
    }

    /**
     * 요청 한 줄 → 견적 항목.
     *
     * <p><b>카탈로그 항목이면 품목명·단위를 카탈로그에서 가져온다</b> — 요청 값을 믿지 않는다.
     * ERD가 "작성 시점 카탈로그 값 복사"로 정하고 있고(QT-24), 클라이언트가 보낸 이름을 그대로
     * 저장하면 "그때 카탈로그가 무엇이었는지"라는 기록의 목적이 사라진다.
     * <b>단가만 요청 값을 쓴다</b> — 담당자가 조정할 수 있는 유일한 값이기 때문이다 (QT-05).
     *
     * <p>직접 입력(productId = null)이면 셋 다 요청 값이고 카탈로그 단가는 남기지 않는다 (QT-03).
     */
    private QuoteItem toItem(AccessContext ctx, QuoteRequests.UpdateQuote.Item line) {
        if (line.productId() == null) {
            return QuoteItem.of(null, line.name(), line.unit(),
                    line.quantity(), line.unitPrice(), null, line.sortOrder());
        }
        if (!productQuery.isSellable(ctx, line.productId())) {
            throw new BusinessException(ErrorCode.PRODUCT_DISCONTINUED);   // PR-06
        }
        ProductQuery.ProductSnapshot product = productQuery.get(ctx, line.productId());   // 없으면 404
        return QuoteItem.of(product.id(), product.name(), product.unit(),
                line.quantity(), line.unitPrice(), product.unitPrice(), line.sortOrder());
    }

    /**
     * 회사 스코프 + 담당 축으로 견적 한 건을 찾는다. 없으면 404 — <b>존재와 권한을 구별하지 않는다</b> (SC-09).
     */
    private Quote findInScope(AccessContext ctx, UUID quoteId) {
        Quote quote = quoteRepository.findWithItemsByIdAndCompanyId(quoteId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireDealInScope(ctx, quote.getDealId());
        return quote;
    }

    /**
     * Deal이 이 요청의 범위 안에 있는지 — 회사가 다르거나 담당이 아니면 404 (SC-01·02·09).
     *
     * <p>{@code assigneeIdOf}는 없는 Deal에 대해 스스로 {@code RESOURCE_NOT_FOUND}를 던진다.
     * 그 던짐이 회사 확인까지 해주지는 않으므로 {@code summariesByIds}로 회사를 먼저 본다 —
     * 그 목록이 비면 "다른 회사의 Deal"이고, 답은 없는 것과 같아야 한다.
     */
    private void requireDealInScope(AccessContext ctx, UUID dealId) {
        if (dealQuery.summariesByIds(ctx.companyId(), List.of(dealId)).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (ctx.scope() == AccessScope.OWNED_ONLY
                && !ctx.memberId().equals(dealQuery.assigneeIdOf(dealId))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    /**
     * 08이 {@code vatMode}를 문자열로 정의해서 여기서 enum으로 바꾼다.
     * 모르는 값은 500이 아니라 <b>400</b>이다 — 잘못 보낸 쪽은 클라이언트다.
     */
    private static Quote.VatMode parseVatMode(String vatMode) {
        try {
            return Quote.VatMode.valueOf(vatMode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
