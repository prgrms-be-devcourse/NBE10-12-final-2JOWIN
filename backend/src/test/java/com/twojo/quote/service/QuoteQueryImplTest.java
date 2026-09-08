package com.twojo.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.QuoteQuery;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.repository.QuoteRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 응답 대기 견적 조회 (NT-05 · DB-03) — <b>무엇을 대상으로 삼는가</b>와 <b>무엇을 실어 보내는가</b>를 본다.
 *
 * <p><b>담당 축을 여기서 거르지 않는 것이 계약이다.</b> 배치에는 {@code AccessContext}가 없어
 * SC-02를 판정할 수 없고, 대신 {@code dealId}·{@code companyId}를 실어 호출자가 거르게 한다
 * (2026-09-08 C·D 합의). 그 두 필드가 빠지면 영업 대시보드는 누수를 막으려 목록을 통째로 비워야 한다 —
 * 그래서 필드 존재 자체를 단언한다.
 */
@ExtendWith(MockitoExtension.class)
class QuoteQueryImplTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();

    @Mock private QuoteRepository quoteRepository;
    @InjectMocks private QuoteQueryImpl quoteQuery;

    private static Quote quoteAt(Quote.Status status) {
        Quote quote = Quote.draft(COMPANY_ID, DEAL_ID, "Q-2609-001", LocalDate.of(2026, 10, 1));
        ReflectionTestUtils.setField(quote, "status", status);
        ReflectionTestUtils.setField(quote, "sentAt", Instant.parse("2026-09-01T00:00:00Z"));
        return quote;
    }

    @Test
    @DisplayName("발송됨·열람됨만 대상이다 — 반려·회수·만료는 이미 끝난 건이다 (전이표 §6)")
    void 대상_상태() {
        given(quoteRepository.findByCompanyIdAndStatusInOrderBySentAtAsc(eq(COMPANY_ID), any()))
                .willReturn(List.of());

        quoteQuery.findAwaitingResponse(COMPANY_ID);

        ArgumentCaptor<Collection<Quote.Status>> statuses = ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(quoteRepository)
                .findByCompanyIdAndStatusInOrderBySentAtAsc(eq(COMPANY_ID), statuses.capture());
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(Quote.Status.SENT, Quote.Status.VIEWED);
    }

    /**
     * {@code dealId}·{@code companyId}가 이 계약의 핵심이다 — 호출자가 범위를 거르고(SC-02),
     * 정지 회사를 억제하는(Q-27) 축이다. 하나라도 빠지면 소비자가 방어적으로 화면을 비운다.
     */
    @Test
    @DisplayName("dealId·companyId가 실린다 — 호출자가 범위를 거르는 축이다")
    void 필드_매핑() {
        Quote quote = quoteAt(Quote.Status.SENT);
        given(quoteRepository.findByCompanyIdAndStatusInOrderBySentAtAsc(any(), any()))
                .willReturn(List.of(quote));

        QuoteQuery.QuoteSummary row = quoteQuery.findAwaitingResponse(COMPANY_ID).getFirst();

        assertThat(row.dealId()).isEqualTo(DEAL_ID);
        assertThat(row.companyId()).isEqualTo(COMPANY_ID);
        assertThat(row.quoteNo()).isEqualTo("Q-2609-001");
        assertThat(row.sentAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(row.validUntil()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    /**
     * 미열람 판정은 {@code firstViewedAt}의 null 여부다 (GAP-08) — 별도 플래그를 두지 않는다.
     * 발송됨은 아직 null, 열람됨은 값이 있다.
     */
    @Test
    @DisplayName("firstViewedAt이 미열람 판정 축이다 — 발송됨은 null")
    void 미열람_판정() {
        given(quoteRepository.findByCompanyIdAndStatusInOrderBySentAtAsc(any(), any()))
                .willReturn(List.of(quoteAt(Quote.Status.SENT)));

        assertThat(quoteQuery.findAwaitingResponse(COMPANY_ID).getFirst().firstViewedAt()).isNull();
    }

    /**
     * <b>지금은 null이다.</b> 고객사는 B 소유라 이 모듈이 직접 읽을 수 없고, {@code CustomerQuery.get}은
     * {@code AccessContext}를 요구하는데 배치에는 그것이 없다. 회사 스코프만으로 이름을 얻는 창구가
     * 열리면 이 테스트가 <b>먼저 깨져서</b> 채워야 할 자리를 알려준다.
     */
    @Test
    @DisplayName("customerName은 아직 null — B 계약에 ctx 없는 이름 조회가 없다")
    void 고객사명_공백() {
        given(quoteRepository.findByCompanyIdAndStatusInOrderBySentAtAsc(any(), any()))
                .willReturn(List.of(quoteAt(Quote.Status.VIEWED)));

        assertThat(quoteQuery.findAwaitingResponse(COMPANY_ID).getFirst().customerName()).isNull();
    }
}
