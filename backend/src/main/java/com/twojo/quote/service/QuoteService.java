package com.twojo.quote.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.AuditActor;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.DealCommand;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.ProductQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.ViewTokenCommand;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.global.sequence.DocumentNumberService;
import com.twojo.global.sequence.DocumentSequence.DocType;
import com.twojo.quote.QuoteSent;
import com.twojo.quote.dto.QuoteRequests;
import com.twojo.quote.dto.QuoteResponses;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.entity.QuoteItem;
import com.twojo.quote.repository.QuoteRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
     * 작성 시작 시점의 임시 유효기간 — 오늘로부터 30일 (KST). <b>Q-47</b> (03 §3, v1.6.7).
     *
     * <p>07·08의 {@code CreateQuoteRequest}는 dealId 하나만 받는데 {@code quote.valid_until}은
     * {@code NOT NULL}이라, 작성 시작 단계에 값이 반드시 있어야 한다. 담당자가 PUT에서 실제
     * 기간을 지정하므로(QT-09) 이 값은 그때까지의 자리표시자이고, 발송 전에는 항상 덮인다 —
     * 08의 {@code UpdateQuoteRequest.validUntil}이 {@code @NotNull @Future}다.
     *
     * <p>대안이던 "컬럼을 nullable로" 는 마이그레이션 + ERD 버전 업이 딸려와 접었다.
     *
     * <p><b>{@code @Future}는 서버 시간대를 쓰고 이 기본값은 KST라 자정 부근에 하루 어긋난다.</b>
     * 자리표시자가 발송 전 항상 덮이는 성질로 흡수되는 차이다 (Q-47).
     */
    private static final int DEFAULT_VALIDITY_DAYS = 30;

    /** 유효기간은 사람이 읽는 날짜라 한국 날짜로 끊는다 — 채번의 연월 판정과 같은 이유 (#72) */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final QuoteRepository quoteRepository;
    private final DealQuery dealQuery;
    private final DealCommand dealCommand;
    private final CustomerQuery customerQuery;
    private final ViewTokenCommand viewTokenCommand;
    private final ProductQuery productQuery;
    private final DocumentNumberService documentNumberService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 작성 시작 (QT-01) — 빈 DRAFT 하나를 만든다. 항목은 이어지는 PUT이 채운다.
     *
     * <p>Deal 하나에 견적을 여러 건 만들 수 있다 (QT-18) — 중복 검사를 하지 않는다.
     */
    @Transactional
    public QuoteResponses.QuoteDetail create(AccessContext ctx, QuoteRequests.CreateQuote request) {
        UUID dealId = request.dealId();
        DealQuery.DealSummary deal = requireDealInScope(ctx, dealId);

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
        return QuoteResponses.QuoteDetail.of(quote, deal.title());
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

    /**
     * 발송 (QT-13~16, AP-01) — <b>한 트랜잭션이다</b> (Q-40).
     *
     * <pre>
     * 범위 판정 → 종결 Deal 차단 → 수신인 검증 → 발송 가능 검사
     *          → 링크 발급(+메일 예약) → Deal 단계 자동 승급 → SENT 전이
     * </pre>
     *
     * <p><b>순서에 이유가 있다.</b>
     * <ul>
     *   <li><b>검증이 전부 앞</b> — 링크를 발급한 뒤 실패하면 고객에게 이미 메일이 예약된
     *       상태로 롤백된다. 되돌릴 수 없는 일을 마지막에 둔다</li>
     *   <li><b>링크 발급이 SENT 전</b> — {@code ViewTokenCommand.issue}가 "issue 시점의
     *       status는 아직 DRAFT"를 계약으로 둔다 (Q-40 순서 합의)</li>
     *   <li><b>단계 승급이 마지막 직전</b> — 승급은 실패할 수 있고(종결 딜),
     *       그때 링크까지 함께 롤백되어야 한다</li>
     * </ul>
     *
     * <p>전이표 §6이 발송의 <b>효과</b>로 "열람 링크 활성 발급"을 규정하므로 링크를 비동기로
     * 빼지 않는다 — "링크 없는 SENT 견적"은 표에 없는 상태다.
     * <b>메일 발송만 커밋 후 비동기</b>이고, 메일 실패는 발송을 되돌리지 않는다.
     */
    @Transactional
    public QuoteResponses.SendResult send(AccessContext ctx, UUID quoteId,
                                          QuoteRequests.SendQuote request) {
        ScopedQuote scoped = findInScope(ctx, quoteId);
        Quote quote = scoped.quote();
        UUID dealId = quote.getDealId();

        // 종결(WON·LOST) Deal에는 발송할 수 없다 (Q-25, 전이표 §6)
        if (!dealQuery.isOpen(dealId)) {
            throw new BusinessException(ErrorCode.QUOTE_DEAL_CLOSED);
        }
        requireContactInCustomer(dealId, request.recipientContactId());
        quote.requireSendable(LocalDate.now(SEOUL));

        viewTokenCommand.issue(quoteId, request.recipientContactId(), request.message());   // message는 메일 본문에 (#183)
        dealCommand.promoteToQuoteStage(dealId);   // 승급이 일어났다면 그 안에서 DealStageChanged(SYSTEM)가 발행된다
        Instant sentAt = Instant.now();
        quote.markSent(sentAt);

        // 발송 감사 이벤트 (AC-07, #22) — requireSendable을 통과한 뒤라 여기 도달하면 반드시 전이다
        eventPublisher.publishEvent(new QuoteSent(quote.getCompanyId(), quoteId, dealId,
                quote.getQuoteNo(), AuditActor.member(ctx.memberId()), sentAt));

        // 승급이 반영된 단계를 다시 읽는다 — 규칙을 여기서 다시 계산하면 전이표와 두 벌이 된다.
        // 같은 트랜잭션이라 조회가 더티 엔티티를 flush시켜 갱신된 값이 온다.
        String dealStage = requireDealInScope(ctx, dealId).stage();
        return new QuoteResponses.SendResult(
                quote.getId(), quote.getStatus().name(), dealStage, quote.getVersion());
    }

    /**
     * 회수 (QT-17) — 발송됨·열람됨 → 회수됨, <b>링크 즉시 만료</b>.
     *
     * <p><b>종결 Deal에서도 된다</b> — 발송과 반대다 (07 §C "정리 목적"). 그래서 여기에는
     * {@code isOpen} 검사가 없다. 발송은 새 약속을 만드는 행위지만 회수는 이미 나간 링크를
     * 닫는 뒷정리라, 딜이 끝난 뒤에 오히려 필요하다.
     *
     * <p>링크 만료는 {@code expire}가 멱등이라 활성 링크가 없어도(수동 만료 뒤 회수 등)
     * 안전하다.
     */
    @Transactional
    public QuoteResponses.QuoteDetail withdraw(AccessContext ctx, UUID quoteId) {
        ScopedQuote scoped = findInScope(ctx, quoteId);
        scoped.quote().withdraw();
        viewTokenCommand.expire(quoteId, ViewTokenCommand.ExpiredReason.WITHDRAWN);

        quoteRepository.flush();   // 응답에 최신 version을 싣는다 (08 검증 노트 #4)
        return QuoteResponses.QuoteDetail.of(scoped.quote(), scoped.dealTitle());
    }

    /**
     * 수신인 변경 재발송 (AP-13) — 기존 활성 링크를 {@code RESENT}로 닫고 새 링크를 발급한다.
     *
     * <p><b>견적 상태는 바뀌지 않는다.</b> 이미 발송된 견적을 다른 사람에게 다시 보내는 것이라
     * SENT·VIEWED 그대로다. 첫 열람 시각도 그대로 둔다 — 그 값이 <b>이전 수신인</b> 기준이라는
     * 한계는 D와 정리했다 (#54 회신).
     *
     * <p><b>수신인 검증은 발송과 같다</b> — 두 경로 모두 C가 맡기로 한 약속이다.
     * 링크를 닫고 새로 여는 것은 {@code issue}가 한 번에 처리한다 (기존 ACTIVE → RESENT → 신규).
     */
    @Transactional
    public void resendViewToken(AccessContext ctx, UUID quoteId,
                                QuoteRequests.ResendViewToken request) {
        Quote quote = findInScope(ctx, quoteId).quote();
        quote.requireResendable(LocalDate.now(SEOUL));
        requireContactInCustomer(quote.getDealId(), request.recipientContactId());

        viewTokenCommand.issue(quoteId, request.recipientContactId(), null);   // 08 ResendViewTokenRequest에는 message가 없다
    }

    /**
     * 열람 링크 수동 만료 (AP-14) — <b>링크만 닫는다. 견적 상태는 그대로다.</b>
     *
     * <p>전이표 §7의 링크 전이일 뿐 §6의 견적 전이가 아니다. 견적이 SENT·VIEWED로 남아 있어야
     * 담당자가 수신인을 바꿔 재발송할 수 있다 — 그래서 {@code requireResendable}의 판정 축이
     * 링크가 아니라 견적 상태다.
     *
     * <p>{@code expire}는 멱등이라 활성 링크가 없어도 예외가 아니다 — 두 번 눌러도 안전하다.
     */
    @Transactional
    public void expireViewToken(AccessContext ctx, UUID quoteId) {
        findInScope(ctx, quoteId);   // 범위 판정만 — 없거나 범위 밖이면 404
        viewTokenCommand.expire(quoteId, ViewTokenCommand.ExpiredReason.MANUAL);
    }

    /**
     * 수신인이 <b>이 Deal의 고객사 소속</b>인지 (QT-13, AP-13).
     *
     * <p><b>C가 맡기로 한 검증이다</b> (D와 2026-09-02 합의) — {@code ViewTokenCommand.issue}
     * 쪽에는 방어 체크를 넣지 않는다. 양쪽에 두면 책임 소재가 흐려지고 나중에 한쪽만 고쳐진다.
     *
     * <p><b>빠뜨리면 무관한 고객사 담당자에게 열람 링크가 나간다.</b>
     * {@code customer_contact}에 {@code company_id}가 없어 복합 FK로 막을 수 없는 영역이고
     * (ERD "DB로 못 막는 것"), 열람 토큰이 곧 인증이라(SC-07) 타사가 견적을 보게 된다.
     */
    private void requireContactInCustomer(UUID dealId, UUID recipientContactId) {
        UUID customerId = dealQuery.customerIdOf(dealId);
        if (!customerQuery.existsContactInCustomer(customerId, recipientContactId)) {
            throw new BusinessException(ErrorCode.CONTACT_NOT_IN_CUSTOMER);
        }
    }

    /** 상세 — 항목 포함 */
    public QuoteResponses.QuoteDetail get(AccessContext ctx, UUID quoteId) {
        ScopedQuote scoped = findInScope(ctx, quoteId);
        return QuoteResponses.QuoteDetail.of(scoped.quote(), scoped.dealTitle());
    }

    /**
     * 발송 전 미리보기 (QT-12) — <b>고객이 보게 될 것과 같은 데이터</b>다.
     *
     * <p>{@code QuoteQueryImpl.toPublicView}를 그대로 쓴다. 여기서 따로 조립하면 미리보기와
     * 실제 열람 화면이 갈리는데, 그러면 미리보기가 확인해 주는 것이 아무것도 없어진다.
     * 다만 <b>범위 판정은 구성원 규칙</b>을 쓴다 — 부르는 쪽이 로그인한 구성원이기 때문이다.
     */
    public QuoteQuery.PublicQuoteView preview(AccessContext ctx, UUID quoteId) {
        return QuoteQueryImpl.toPublicView(findInScope(ctx, quoteId).quote());
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
        ScopedQuote scoped = findInScope(ctx, quoteId);
        Quote quote = scoped.quote();
        quote.checkVersion(request.version());

        quote.update(request.validUntil(), parseVatMode(request.vatMode()), request.terms());
        quote.replaceItems(request.items().stream().map(line -> toItem(ctx, line)).toList());

        // @Version 증가를 응답에 반영한다 (08 검증 노트 #4 — Response는 항상 최신 version).
        // 없으면 flush가 커밋 시점에 일어나 응답에는 읽어온 값이 실리고, 그 version으로
        // 다음 저장을 하면 409가 난다 — 편집기의 두 번째 저장부터 막힌다.
        quoteRepository.flush();

        return QuoteResponses.QuoteDetail.of(quote, scoped.dealTitle());
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
    private ScopedQuote findInScope(AccessContext ctx, UUID quoteId) {
        Quote quote = quoteRepository.findWithItemsByIdAndCompanyId(quoteId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        DealQuery.DealSummary deal = requireDealInScope(ctx, quote.getDealId());
        return new ScopedQuote(quote, deal.title());
    }

    /**
     * 범위 판정을 통과한 견적과 그 Deal의 제목.
     *
     * <p>제목을 따로 조회하지 않으려고 함께 들고 다닌다 — 범위 판정이 이미 Deal 요약을
     * 가져오므로, 응답에 {@code dealTitle}을 채우는 데 드는 추가 조회가 없다.
     */
    private record ScopedQuote(Quote quote, String dealTitle) {
    }

    /**
     * Deal이 이 요청의 범위 안에 있는지 — 회사가 다르거나 담당이 아니면 404 (SC-01·02·09).
     *
     * <p>{@code assigneeIdOf}는 없는 Deal에 대해 스스로 {@code RESOURCE_NOT_FOUND}를 던진다.
     * 그 던짐이 회사 확인까지 해주지는 않으므로 {@code summariesByIds}로 회사를 먼저 본다 —
     * 그 목록이 비면 "다른 회사의 Deal"이고, 답은 없는 것과 같아야 한다.
     */
    private DealQuery.DealSummary requireDealInScope(AccessContext ctx, UUID dealId) {
        DealQuery.DealSummary deal = dealQuery.summariesByIds(ctx.companyId(), List.of(dealId))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (ctx.scope() == AccessScope.OWNED_ONLY
                && !ctx.memberId().equals(dealQuery.assigneeIdOf(dealId))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return deal;
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
