package com.twojo.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.twojo.boundary.QuoteCommand;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.boundary.AuditActorType;
import com.twojo.quote.QuoteApproved;
import com.twojo.quote.QuoteRejected;
import com.twojo.quote.QuoteViewed;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.repository.QuoteRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link QuoteCommand} 구현 — 조회·위임·시각 기록을 본다.
 *
 * <p>상태별 허용·차단 규칙은 엔티티의 몫이라 {@code QuoteResponseTransitionTest}가 맡는다.
 * 여기서 보는 것은 <b>구현체가 더하는 것</b> 셋이다: 없는 견적을 어떻게 다루는가,
 * 엔티티에 제대로 위임하는가, 응답 시각이 실제로 채워지는가.
 */
@ExtendWith(MockitoExtension.class)
class QuoteCommandImplTest {

    private static final UUID QUOTE_ID = UUID.randomUUID();

    @Mock private QuoteRepository quoteRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private QuoteCommandImpl quoteCommand;

    private static Quote quoteAt(Quote.Status status) {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID(),
                "Q-2609-001", LocalDate.now().plusDays(30));
        ReflectionTestUtils.setField(quote, "status", status);
        return quote;
    }

    private void exists(Quote quote) {
        given(quoteRepository.findById(QUOTE_ID)).willReturn(Optional.of(quote));
    }

    @Test
    @DisplayName("열람을 엔티티에 위임하고 첫 열람 시각을 채운다")
    void 열람() {
        Quote quote = quoteAt(Quote.Status.SENT);
        exists(quote);

        quoteCommand.markViewed(QUOTE_ID);

        assertThat(quote.getStatus()).isEqualTo(Quote.Status.VIEWED);
        assertThat(quote.getFirstViewedAt()).isNotNull();   // 시각은 구현체가 정한다
    }

    @Test
    @DisplayName("승인 시 응답자와 응답 시각이 채워진다")
    void 승인() {
        Quote quote = quoteAt(Quote.Status.VIEWED);
        exists(quote);

        quoteCommand.approve(QUOTE_ID, new QuoteCommand.Responder("김서연", "구매팀장"));

        assertThat(quote.getStatus()).isEqualTo(Quote.Status.APPROVED);
        assertThat(quote.getResponderName()).isEqualTo("김서연");
        assertThat(quote.getResponderTitle()).isEqualTo("구매팀장");
        assertThat(quote.getRespondedAt()).isNotNull();
    }

    @Test
    @DisplayName("반려 시 사유가 함께 기록된다")
    void 반려() {
        Quote quote = quoteAt(Quote.Status.VIEWED);
        exists(quote);

        quoteCommand.reject(QUOTE_ID, "예산 초과", new QuoteCommand.Responder("김서연", null));

        assertThat(quote.getStatus()).isEqualTo(Quote.Status.REJECTED);
        assertThat(quote.getRejectReason()).isEqualTo("예산 초과");
        assertThat(quote.getRespondedAt()).isNotNull();
    }

    /**
     * 세 경로 모두 404다 — 조용히 무동작하면 <b>고객은 승인했다고 믿는데 견적은 그대로인</b>
     * 상태가 남는다. D가 토큰을 RESPONDED로 소진한 뒤라 되돌릴 방법도 없다.
     */
    @Test
    @DisplayName("없는 견적이면 세 메서드 모두 RESOURCE_NOT_FOUND")
    void 없는_견적() {
        given(quoteRepository.findById(QUOTE_ID)).willReturn(Optional.empty());
        QuoteCommand.Responder 응답자 = new QuoteCommand.Responder("김서연", null);

        assertThatThrownBy(() -> quoteCommand.markViewed(QUOTE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> quoteCommand.approve(QUOTE_ID, 응답자))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> quoteCommand.reject(QUOTE_ID, "사유", 응답자))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("상태가 어긋난 승인은 엔티티의 예외가 그대로 전파된다 — 구현체가 삼키지 않는다")
    void 차단_예외_전파() {
        exists(quoteAt(Quote.Status.SENT));   // 열람 전이라 승인할 수 없다 (전이표 §6)

        assertThatThrownBy(() -> quoteCommand.approve(QUOTE_ID, new QuoteCommand.Responder("김서연", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.QUOTE_NOT_RESPONDABLE);
    }

    /**
     * 고객 응답 감사 이벤트 (AC-07, #22) — 세 이벤트 모두 행위자가 {@code CUSTOMER_LINK}다.
     * 계정 없는 고객이 링크로 한 일이라 {@code actorId}가 존재하지 않는다.
     */
    @Test
    @DisplayName("첫 열람은 QuoteViewed(CUSTOMER_LINK)를 발행한다")
    void 열람_발행() {
        Quote quote = quoteAt(Quote.Status.SENT);
        exists(quote);

        quoteCommand.markViewed(QUOTE_ID);

        ArgumentCaptor<QuoteViewed> event = ArgumentCaptor.forClass(QuoteViewed.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().quoteNo()).isEqualTo("Q-2609-001");
        assertThat(event.getValue().dealId()).isEqualTo(quote.getDealId());
        assertThat(event.getValue().companyId()).isEqualTo(quote.getCompanyId());
        assertThat(event.getValue().actor().type()).isEqualTo(AuditActorType.CUSTOMER_LINK);
        assertThat(event.getValue().actor().actorId()).isNull();
    }

    /**
     * <b>재열람은 사건이 아니다.</b> {@code markViewed}는 멱등이라 이미 열람했거나 응답을 마친
     * 견적에서는 아무 일도 하지 않는데(AP-07 · 전이표 §7), 그때도 발행하면 고객이 링크를
     * 새로고침할 때마다 타임라인에 열람 기록이 한 줄씩 쌓인다.
     */
    @Test
    @DisplayName("재열람·응답 후 열람은 발행하지 않는다 — 첫 열람만이 사건이다 (AP-07)")
    void 재열람_미발행() {
        exists(quoteAt(Quote.Status.VIEWED));

        quoteCommand.markViewed(QUOTE_ID);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("승인은 응답자 이름을 실어 QuoteApproved를 발행한다")
    void 승인_발행() {
        exists(quoteAt(Quote.Status.VIEWED));

        quoteCommand.approve(QUOTE_ID, new QuoteCommand.Responder("김서연", "구매팀장"));

        ArgumentCaptor<QuoteApproved> event = ArgumentCaptor.forClass(QuoteApproved.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().responderName()).isEqualTo("김서연");
        assertThat(event.getValue().actor().type()).isEqualTo(AuditActorType.CUSTOMER_LINK);
    }

    @Test
    @DisplayName("반려는 사유까지 실어 QuoteRejected를 발행한다 (AP-10)")
    void 반려_발행() {
        exists(quoteAt(Quote.Status.VIEWED));

        quoteCommand.reject(QUOTE_ID, "예산 초과", new QuoteCommand.Responder("김서연", null));

        ArgumentCaptor<QuoteRejected> event = ArgumentCaptor.forClass(QuoteRejected.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().reason()).isEqualTo("예산 초과");
        assertThat(event.getValue().responderName()).isEqualTo("김서연");
    }

    /** 엔티티가 막은 전이는 사건이 아니다 — 승인되지 않은 견적의 승인 시도가 타임라인에 남으면 안 된다 */
    @Test
    @DisplayName("상태가 어긋나 차단된 승인은 발행하지 않는다")
    void 차단시_미발행() {
        exists(quoteAt(Quote.Status.SENT));

        assertThatThrownBy(() -> quoteCommand.approve(QUOTE_ID, new QuoteCommand.Responder("김서연", null)))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(Object.class));
    }
}
