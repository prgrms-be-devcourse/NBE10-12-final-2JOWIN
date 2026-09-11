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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * {@link ExpiringQuoteBatch} — 회사 그룹핑(회사당 get 1회), Q-27 정지 회사 스킵, 2단 격리(회사·견적),
 * {@code findExpiringBetween} 구간 인자를 검증한다. 실 스케줄 트리거·실 PG는
 * {@code ExpiringQuoteIntegrationTest}가 덮는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExpiringQuoteBatchTest {

    private static final int BEFORE_DAYS = 3;

    private static final UUID COMPANY_A = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("c0000000-0000-4000-8000-000000000002");
    private static final UUID DEAL = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID QUOTE_1 = UUID.fromString("6b000000-0000-4000-8000-000000000001");
    private static final UUID QUOTE_2 = UUID.fromString("6b000000-0000-4000-8000-000000000002");
    private static final UUID QUOTE_3 = UUID.fromString("6b000000-0000-4000-8000-000000000003");

    @Mock private CompanyQuery companyQuery;
    @Mock private QuoteQuery quoteQuery;
    @Mock private ExpiringQuoteWorker worker;

    private ExpiringQuoteBatch batch;

    @BeforeEach
    void setUp() {
        batch = new ExpiringQuoteBatch(companyQuery, quoteQuery, worker, BEFORE_DAYS, "Asia/Seoul");
    }

    private QuoteQuery.QuoteSummary quote(UUID id, UUID companyId) {
        return new QuoteQuery.QuoteSummary(id, "Q-" + id, DEAL, companyId, "고객사",
                Instant.parse("2026-09-01T00:00:00Z"), null, LocalDate.of(2026, 12, 31));
    }

    private CompanySummary company(UUID id, boolean active) {
        return new CompanySummary(id, "회사-" + id, "000-00-00000", active);
    }

    @Test
    @DisplayName("후보를 회사별로 묶어 회사당 get을 1회만 부르고 견적마다 worker에 위임한다")
    void 회사_그룹핑() {
        given(quoteQuery.findExpiringBetween(any(), any())).willReturn(List.of(
                quote(QUOTE_1, COMPANY_A), quote(QUOTE_2, COMPANY_A), quote(QUOTE_3, COMPANY_B)));
        given(companyQuery.get(COMPANY_A)).willReturn(company(COMPANY_A, true));
        given(companyQuery.get(COMPANY_B)).willReturn(company(COMPANY_B, true));

        batch.run();

        then(companyQuery).should(times(1)).get(COMPANY_A);
        then(companyQuery).should(times(1)).get(COMPANY_B);
        then(worker).should(times(3)).remind(any(), any());
    }

    @Test
    @DisplayName("findExpiringBetween을 오늘 ~ 오늘+before-days 구간으로 부른다")
    void 구간_인자() {
        given(quoteQuery.findExpiringBetween(any(), any())).willReturn(List.of());

        batch.run();

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        then(quoteQuery).should().findExpiringBetween(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")));
        assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(BEFORE_DAYS));
    }

    @Test
    @DisplayName("정지된 회사는 견적을 worker에 넘기지 않는다 (Q-27)")
    void 정지_회사_스킵() {
        given(quoteQuery.findExpiringBetween(any(), any()))
                .willReturn(List.of(quote(QUOTE_1, COMPANY_A)));
        given(companyQuery.get(COMPANY_A)).willReturn(company(COMPANY_A, false));

        batch.run();

        then(worker).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("한 회사에서 예외가 나도 다음 회사는 정상 처리한다 (회사 단위 격리)")
    void 회사_단위_격리() {
        given(quoteQuery.findExpiringBetween(any(), any())).willReturn(List.of(
                quote(QUOTE_1, COMPANY_A), quote(QUOTE_3, COMPANY_B)));
        given(companyQuery.get(COMPANY_A)).willThrow(new RuntimeException("db blip"));
        given(companyQuery.get(COMPANY_B)).willReturn(company(COMPANY_B, true));

        assertThatCode(() -> batch.run()).doesNotThrowAnyException();

        then(worker).should().remind(any(), eq("회사-" + COMPANY_B));
    }

    @Test
    @DisplayName("한 견적에서 예외가 나도 같은 회사 다음 견적은 시도된다 (견적 단위 격리)")
    void 견적_단위_격리() {
        given(quoteQuery.findExpiringBetween(any(), any())).willReturn(List.of(
                quote(QUOTE_1, COMPANY_A), quote(QUOTE_2, COMPANY_A)));
        given(companyQuery.get(COMPANY_A)).willReturn(company(COMPANY_A, true));
        willThrow(new RuntimeException("boom")).given(worker).remind(any(), any());

        assertThatCode(() -> batch.run()).doesNotThrowAnyException();

        then(worker).should(times(2)).remind(any(), any());
    }

    @Test
    @DisplayName("후보가 없으면 회사 조회도 worker 위임도 하지 않는다")
    void 빈_후보() {
        given(quoteQuery.findExpiringBetween(any(), any())).willReturn(List.of());

        batch.run();

        then(companyQuery).should(never()).get(any());
        then(worker).shouldHaveNoInteractions();
    }
}
