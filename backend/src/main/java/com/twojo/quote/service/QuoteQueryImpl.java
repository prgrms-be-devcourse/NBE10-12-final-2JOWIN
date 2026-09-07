package com.twojo.quote.service;

import com.twojo.boundary.QuoteQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.entity.QuoteItem;
import com.twojo.quote.repository.QuoteRepository;
import java.time.LocalDate;
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

    private final QuoteRepository quoteRepository;

    @Override
    public List<QuoteSummary> findAwaitingResponse(UUID companyId) {
        throw new UnsupportedOperationException("QuoteQuery.findAwaitingResponse — C 3주차 구현 예정");
    }

    @Override
    public List<QuoteSummary> findExpiringUntil(LocalDate date) {
        throw new UnsupportedOperationException("QuoteQuery.findExpiringUntil — C 3주차 구현 예정");
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
     * 엔티티 → 열람 데이터. {@code QuoteService}의 미리보기(QT-12)도 이걸 쓴다 —
     * <b>미리보기와 고객이 실제로 보는 화면이 갈리면 미리보기의 의미가 없다.</b>
     */
    public static PublicQuoteView toPublicView(Quote quote) {
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
