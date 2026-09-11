package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.CustomerQuery.ContactSummary;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MailCommand.TemplateType;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.ViewTokenQuery;
import com.twojo.notification.repository.EmailLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ExpiringQuoteWorker} — 수신 연락처 조회, 견적당 1회 멱등(email_log 사전 체크), 이메일 정규화를 검증한다.
 * {@code REQUIRES_NEW} 경계와 실 PG 저장은 {@code ExpiringQuoteIntegrationTest}가 덮는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExpiringQuoteWorkerTest {

    private static final UUID COMPANY = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID DEAL = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID QUOTE = UUID.fromString("6b000000-0000-4000-8000-000000000001");
    private static final UUID CONTACT = UUID.fromString("cc000000-0000-4000-8000-000000000001");
    private static final String COMPANY_NAME = "제조사";

    @Mock private ViewTokenQuery viewTokenQuery;
    @Mock private CustomerQuery customerQuery;
    @Mock private EmailLogRepository emailLogRepository;
    @Mock private MailCommand mailCommand;
    @InjectMocks private ExpiringQuoteWorker worker;

    private QuoteQuery.QuoteSummary quote() {
        return new QuoteQuery.QuoteSummary(QUOTE, "Q-NT06-001", DEAL, COMPANY, "고객사",
                Instant.parse("2026-09-01T00:00:00Z"), null, LocalDate.of(2026, 9, 30));
    }

    private void contact(String email) {
        given(viewTokenQuery.recipientContactIdOf(QUOTE)).willReturn(Optional.of(CONTACT));
        given(customerQuery.getContact(CONTACT)).willReturn(new ContactSummary(CONTACT, "담당", "과장", email));
    }

    @Test
    @DisplayName("수신 연락처 있고 미예약이면 QUOTE_EXPIRING 메일을 예약한다")
    void 정상_예약() {
        contact("buyer@customer.test");
        given(emailLogRepository.existsByTemplateTypeAndRefIdAndRecipientEmail(
                TemplateType.QUOTE_EXPIRING, QUOTE, "buyer@customer.test")).willReturn(false);

        worker.remind(quote(), COMPANY_NAME);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        then(mailCommand).should().schedule(eq(TemplateType.QUOTE_EXPIRING), eq(COMPANY),
                eq("buyer@customer.test"), eq(QUOTE), subject.capture(), any());
        assertThat(subject.getValue()).contains(COMPANY_NAME).contains("Q-NT06-001");
    }

    @Test
    @DisplayName("활성 열람 토큰이 없으면 예약하지 않는다 (로그만)")
    void 토큰_부재() {
        given(viewTokenQuery.recipientContactIdOf(QUOTE)).willReturn(Optional.empty());

        worker.remind(quote(), COMPANY_NAME);

        then(customerQuery).shouldHaveNoInteractions();
        then(mailCommand).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 QUOTE_EXPIRING 예약이 있으면 예약하지 않는다 (견적당 1회)")
    void 멱등_스킵() {
        contact("buyer@customer.test");
        given(emailLogRepository.existsByTemplateTypeAndRefIdAndRecipientEmail(
                TemplateType.QUOTE_EXPIRING, QUOTE, "buyer@customer.test")).willReturn(true);

        worker.remind(quote(), COMPANY_NAME);

        then(mailCommand).should(never()).schedule(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("수신 이메일을 trim·소문자로 정규화해 멱등 체크·예약한다")
    void 이메일_정규화() {
        contact("  Buyer@Customer.TEST  ");
        given(emailLogRepository.existsByTemplateTypeAndRefIdAndRecipientEmail(
                TemplateType.QUOTE_EXPIRING, QUOTE, "buyer@customer.test")).willReturn(false);

        worker.remind(quote(), COMPANY_NAME);

        then(mailCommand).should().schedule(any(), any(), eq("buyer@customer.test"), any(), any(), any());
    }
}
