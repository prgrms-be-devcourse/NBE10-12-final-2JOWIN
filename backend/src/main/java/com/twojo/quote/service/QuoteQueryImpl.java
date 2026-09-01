package com.twojo.quote.service;

import com.twojo.boundary.QuoteQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link QuoteQuery} 스텁 — 빈 자리만 채운다.
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
public class QuoteQueryImpl implements QuoteQuery {

    @Override
    public List<QuoteSummary> findAwaitingResponse(UUID companyId) {
        throw new UnsupportedOperationException("QuoteQuery.findAwaitingResponse — C 3주차 구현 예정");
    }

    @Override
    public List<QuoteSummary> findExpiringUntil(LocalDate date) {
        throw new UnsupportedOperationException("QuoteQuery.findExpiringUntil — C 3주차 구현 예정");
    }

    @Override
    public PublicQuoteView getPublicView(UUID quoteId) {
        throw new UnsupportedOperationException("QuoteQuery.getPublicView — C 2주차 구현 예정");
    }
}
