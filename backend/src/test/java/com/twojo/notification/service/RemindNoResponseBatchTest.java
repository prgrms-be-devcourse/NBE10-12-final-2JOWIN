package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.CompanyQuery.CompanySummary;
import com.twojo.boundary.QuoteQuery;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RemindNoResponseBatch} — 활성 회사 순회, Q-27 정지 회사 스킵, 임계일수 필터,
 * 2단 격리(회사·견적)를 검증한다. 실 스케줄 트리거·실 PG는 {@link RemindNoResponseIntegrationTest}가 덮는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RemindNoResponseBatchTest {

    private static final int AFTER_DAYS = 3;

    private static final UUID COMPANY_A = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("c0000000-0000-4000-8000-000000000002");
    private static final UUID DEAL = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID QUOTE_OLD = UUID.fromString("6b000000-0000-4000-8000-000000000001");
    private static final UUID QUOTE_RECENT = UUID.fromString("6b000000-0000-4000-8000-000000000002");

    @Mock
    private CompanyQuery companyQuery;
    @Mock
    private QuoteQuery quoteQuery;
    @Mock
    private RemindWorker remindWorker;

    private RemindNoResponseBatch batch;

    @BeforeEach
    void setUp() {
        batch = new RemindNoResponseBatch(companyQuery, quoteQuery, remindWorker, AFTER_DAYS);
    }

    private QuoteQuery.QuoteSummary quote(UUID id, UUID companyId, Instant sentAt) {
        return new QuoteQuery.QuoteSummary(id, "Q-" + id, DEAL, companyId, null,
                sentAt, null, LocalDate.of(2026, 12, 31));
    }

    private Instant daysAgo(long days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    private CompanySummary company(UUID id, boolean active) {
        return new CompanySummary(id, "회사", "000-00-00000", active);
    }

    @Test
    @DisplayName("활성 회사마다 임계일수가 지난 견적을 worker에 위임한다")
    void 활성_회사_순회() {
        given(companyQuery.findActiveIds()).willReturn(List.of(COMPANY_A, COMPANY_B));
        given(companyQuery.get(COMPANY_A)).willReturn(company(COMPANY_A, true));
        given(companyQuery.get(COMPANY_B)).willReturn(company(COMPANY_B, true));
        given(quoteQuery.findAwaitingResponse(COMPANY_A))
                .willReturn(List.of(quote(QUOTE_OLD, COMPANY_A, daysAgo(5))));
        given(quoteQuery.findAwaitingResponse(COMPANY_B))
                .willReturn(List.of(quote(QUOTE_RECENT, COMPANY_B, daysAgo(4))));

        batch.run();

        then(remindWorker).should().remind(eq(COMPANY_A), any());
        then(remindWorker).should().remind(eq(COMPANY_B), any());
    }

    @Test
    @DisplayName("정지된 회사는 견적 조회도 하지 않고 건너뛴다 (Q-27)")
    void 정지_회사_스킵() {
        given(companyQuery.findActiveIds()).willReturn(List.of(COMPANY_A));
        given(companyQuery.get(COMPANY_A)).willReturn(company(COMPANY_A, false));

        batch.run();

        then(quoteQuery).shouldHaveNoInteractions();
        then(remindWorker).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("임계일수에 못 미친 견적은 위임하지 않는다")
    void 임계_미달_견적_제외() {
        given(companyQuery.findActiveIds()).willReturn(List.of(COMPANY_A));
        given(companyQuery.get(COMPANY_A)).willReturn(company(COMPANY_A, true));
        given(quoteQuery.findAwaitingResponse(COMPANY_A)).willReturn(List.of(
                quote(QUOTE_RECENT, COMPANY_A, daysAgo(1)),
                quote(QUOTE_OLD, COMPANY_A, daysAgo(5))));

        batch.run();

        ArgumentCaptor<QuoteQuery.QuoteSummary> captor =
                ArgumentCaptor.forClass(QuoteQuery.QuoteSummary.class);
        then(remindWorker).should().remind(eq(COMPANY_A), captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(QUOTE_OLD);
    }

    @Test
    @DisplayName("sentAt이 null인 견적은 건너뛴다")
    void sentAt_null_견적_제외() {
        given(companyQuery.findActiveIds()).willReturn(List.of(COMPANY_A));
        given(companyQuery.get(COMPANY_A)).willReturn(company(COMPANY_A, true));
        given(quoteQuery.findAwaitingResponse(COMPANY_A))
                .willReturn(List.of(quote(QUOTE_OLD, COMPANY_A, null)));

        batch.run();

        then(remindWorker).should(never()).remind(any(), any());
    }

    @Test
    @DisplayName("한 회사에서 예외가 나도 다음 회사는 정상 처리한다 (회사 단위 격리)")
    void 회사_단위_격리() {
        given(companyQuery.findActiveIds()).willReturn(List.of(COMPANY_A, COMPANY_B));
        given(companyQuery.get(COMPANY_A)).willThrow(new RuntimeException("db blip"));
        given(companyQuery.get(COMPANY_B)).willReturn(company(COMPANY_B, true));
        given(quoteQuery.findAwaitingResponse(COMPANY_B))
                .willReturn(List.of(quote(QUOTE_OLD, COMPANY_B, daysAgo(5))));

        assertThatCode(() -> batch.run()).doesNotThrowAnyException();

        then(remindWorker).should().remind(eq(COMPANY_B), any());
    }

    @Test
    @DisplayName("한 견적에서 예외가 나도 같은 회사 다음 견적은 시도된다 (견적 단위 격리)")
    void 견적_단위_격리() {
        given(companyQuery.findActiveIds()).willReturn(List.of(COMPANY_A));
        given(companyQuery.get(COMPANY_A)).willReturn(company(COMPANY_A, true));
        given(quoteQuery.findAwaitingResponse(COMPANY_A)).willReturn(List.of(
                quote(QUOTE_OLD, COMPANY_A, daysAgo(5)),
                quote(QUOTE_RECENT, COMPANY_A, daysAgo(5))));
        willThrow(new RuntimeException("boom")).given(remindWorker).remind(any(), any());

        assertThatCode(() -> batch.run()).doesNotThrowAnyException();

        then(remindWorker).should(times(2)).remind(any(), any());
    }

    @Test
    @DisplayName("활성 회사가 없으면 아무 것도 하지 않는다")
    void 활성_회사_없음() {
        given(companyQuery.findActiveIds()).willReturn(List.of());

        batch.run();

        then(remindWorker).shouldHaveNoInteractions();
    }
}
