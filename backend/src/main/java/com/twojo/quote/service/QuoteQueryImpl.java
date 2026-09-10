package com.twojo.quote.service;

import com.twojo.boundary.QuoteQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.entity.QuoteItem;
import com.twojo.quote.repository.QuoteRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link QuoteQuery} 구현 — {@code getPublicView}는 실구현, 나머지 둘은 아직 스텁이다.
 *
 * <p>D의 열람 API({@code getPublicView})와 배치·대시보드(NT-05·06, DB-03)가 이 빈을 주입받는다.
 * 실제 구현은 견적 이슈에서 이 클래스의 {@code throw}를 대체한다.
 *
 * <p><b>조회 스텁은 빈 목록이 아니라 예외를 던진다.</b> 빈 목록을 돌려주면
 * "응답 대기 견적이 없다" 같은 <b>틀린 답이 조용히</b> 나가고, 호출자는 정상 결과로 취급한다.
 * 커맨드의 멱등 no-op({@code ViewTokenCommand.expire})과는 성질이 다르다 —
 * 거기서는 "아무 일도 안 함"이 계약 자체였다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteQueryImpl implements QuoteQuery {

    /** 응답 대기 = 발송됨·열람됨 (전이표 §6) — 반려·회수·만료는 이미 끝난 건이다 */
    private static final List<Quote.Status> AWAITING_RESPONSE =
            List.of(Quote.Status.SENT, Quote.Status.VIEWED);

    private final QuoteRepository quoteRepository;

    /**
     * 응답 대기 견적 (NT-05 리마인드 · DB-03 카드) — 발송됨·열람됨.
     *
     * <p><b>회사 전체를 돌려주고 담당 축은 거르지 않는다.</b> 배치에는 {@code AccessContext}가 없어
     * 여기서 SC-02를 판정할 수 없고, 그래서 계약이 {@code dealId}를 함께 준다 —
     * 대시보드가 그 축으로 직접 거른다 (계약 javadoc, 2026-09-08 C·D 합의).
     *
     * <p><b>{@code customerName}은 아직 null이다.</b> 고객사는 B 소유 테이블이라 이 모듈이 직접 읽을 수 없고
     * ({@code ModularityTests}), {@code CustomerQuery.get}은 {@code AccessContext}를 요구하는데
     * 배치 경로에는 그것이 없다. <b>회사 스코프만으로 이름을 얻는 창구가 B 계약에 없다</b> —
     * 그 한 칸이 열리면 여기서 채운다. 그때까지 이름이 필요한 화면은 호출자가 자기 ctx로 조회한다.
     */
    @Override
    public List<QuoteSummary> findAwaitingResponse(UUID companyId) {
        return quoteRepository
                .findByCompanyIdAndStatusInOrderBySentAtAsc(companyId, AWAITING_RESPONSE)
                .stream()
                .map(QuoteQueryImpl::toSummary)
                .toList();
    }

    /** 엔티티 → 요약. {@code customerName}은 위 javadoc의 이유로 아직 채우지 못한다 */
    private static QuoteSummary toSummary(Quote quote) {
        return new QuoteSummary(quote.getId(), quote.getQuoteNo(), quote.getDealId(), quote.getCompanyId(),
                null, quote.getSentAt(), quote.getFirstViewedAt(), quote.getValidUntil());
    }

    @Override
    public List<QuoteSummary> findExpiringUntil(LocalDate date) {
        throw new UnsupportedOperationException("QuoteQuery.findExpiringUntil — C 3주차 구현 예정");
    }

    /**
     * 주문 조회가 쓰는 견적 출처 (OD-08·09) — 회사 스코프가 걸린다 (SC-01).
     * <p>빈 목록이면 조회하지 않는다. 없는 id는 결과에서 빠진다 — 계약대로 예외가 아니다.
     */
    @Override
    public List<QuoteOrigin> originsByIds(UUID companyId, Collection<UUID> quoteIds) {
        if (quoteIds == null || quoteIds.isEmpty()) {
            return List.of();
        }
        return quoteRepository.findByCompanyIdAndIdIn(companyId, quoteIds).stream()
                .map(quote -> new QuoteOrigin(quote.getId(), quote.getQuoteNo(), quote.getDealId()))
                .toList();
    }

    /**
     * 주문 목록의 범위 필터 (SC-04) — 담당 Deal 묶음에 걸린 견적 id 전체.
     * <p><b>빈 목록을 넘기면 빈 목록이다</b> — 담당 Deal이 없는 영업에게 회사 전체 주문이
     * 보이는 사고를 여기서 끊는다 ({@code QuoteSpecs.dealIdIn}과 같은 판단).
     */
    @Override
    public List<UUID> quoteIdsByDeals(UUID companyId, Collection<UUID> dealIds) {
        if (dealIds == null || dealIds.isEmpty()) {
            return List.of();
        }
        return quoteRepository.findIdsByDeals(companyId, dealIds);
    }

    /**
     * 열람 페이지 렌더 데이터 (AP-02·07, QT-25).
     *
     * <p><b>회사 스코프를 걸지 않는다.</b> 고객 열람 링크는 로그인한 요청이 아니라 companyId를
     * 들고 오지 못하고, 대신 토큰이 이미 견적 하나를 특정한다 (11 §7.2).
     * 이 메서드를 구성원 요청 경로에서 쓰면 회사 격리가 뚫린다 — 그쪽은 {@code QuoteService}다.
     *
     * <p>항목은 <b>발송 시점 값 복사본</b>을 그대로 싣는다 — product 조인이 아니다.
     * 카탈로그 상품명이 바뀌어도 고객이 본 견적서와 조회본이 갈리지 않는다 (QT-24, PR-04).
     */
    @Override
    public PublicQuoteView getPublicView(UUID quoteId) {
        Quote quote = quoteRepository.findWithItemsById(quoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return toPublicView(quote);
    }

    /**
     * 엔티티 → 열람 데이터 — 이 계약이 돌려주는 <b>quote 소유분</b>이다.
     *
     * <p><b>이것만으로는 고객 화면이 되지 않는다.</b> 회사 정체성·담당자·{@code respondable}은
     * 다른 모듈에서 오고, 그 조립은 {@code PublicQuoteAssembler}가 한다. 예전에 미리보기(QT-12)가
     * 이 값을 그대로 응답으로 내보내 프론트가 크래시했다 — boundary 계약과 API 응답을 같은 것으로
     * 본 실수였다 (#114). 그래서 모듈 밖으로 열지 않는다.
     */
    private static PublicQuoteView toPublicView(Quote quote) {
        return new PublicQuoteView(
                quote.getId(), quote.getQuoteNo(), quote.getStatus().name(),
                quote.getVatMode().name(), quote.getTerms(), quote.getValidUntil(),
                quote.getSupplyAmount(), quote.getVatAmount(), quote.getTotalAmount(),
                quote.getItems().stream().map(QuoteQueryImpl::toItem).toList(),
                quote.getDealId(), quote.getCompanyId());
    }

    private static PublicQuoteView.Item toItem(QuoteItem item) {
        return new PublicQuoteView.Item(item.getName(), item.getUnit(), item.getQuantity(),
                item.getUnitPrice(), item.getAmount(), item.getSortOrder());
    }
}
