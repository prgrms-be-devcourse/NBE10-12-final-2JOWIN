package com.twojo.quote.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.quote.entity.Quote.Status;
import com.twojo.quote.entity.Quote.VatMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 복제 (QT-19) — 전이표 §6의 "모든 상태 → 복제 → 새 작성 중(DRAFT) · 원본은 그대로" 행.
 *
 * <p>여기서 고정하는 것은 <b>무엇을 베끼고 무엇을 베끼지 않는가</b>다. 발송 이력이나 응답을
 * 함께 베끼면 새 견적이 "보낸 적 있는 견적"으로 태어나고, 항목을 얕게 복사하면 새 견적을
 * 고칠 때 원본까지 바뀐다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QuoteCloneTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final LocalDate 새_유효기간 = LocalDate.of(2026, 10, 11);

    /** 발송·열람·반려까지 겪은 원본 — 베끼지 말아야 할 것이 다 들어 있다 */
    private static Quote 원본(Status status) {
        Quote quote = Quote.draft(COMPANY_ID, DEAL_ID, "Q-2609-001", LocalDate.of(2026, 9, 1));
        ReflectionTestUtils.setField(quote, "id", UUID.randomUUID());
        quote.changeVatMode(VatMode.INCLUDED);
        quote.update(LocalDate.of(2026, 9, 1), VatMode.INCLUDED, "결제는 납품 후 30일 이내");
        quote.replaceItems(List.of(
                QuoteItem.of(null, "책상", "EA", 3, 200_000L, null, 0),
                QuoteItem.of(UUID.randomUUID(), "의자", "EA", 6, 80_000L, 85_000L, 1)));
        ReflectionTestUtils.setField(quote, "status", status);
        ReflectionTestUtils.setField(quote, "sentAt", Instant.parse("2026-09-02T00:00:00Z"));
        ReflectionTestUtils.setField(quote, "firstViewedAt", Instant.parse("2026-09-03T00:00:00Z"));
        ReflectionTestUtils.setField(quote, "respondedAt", Instant.parse("2026-09-04T00:00:00Z"));
        ReflectionTestUtils.setField(quote, "rejectReason", "예산 초과");
        ReflectionTestUtils.setField(quote, "responderName", "이수정");
        return quote;
    }

    /**
     * 전이표가 "모든 상태"로 적고 있다 — 반려된 견적을 고쳐 다시 보내는 것이 주 용도이고(Q-18),
     * 회수·만료된 건도 재제안 출발점이 된다(DL-12).
     */
    @ParameterizedTest(name = "{0} 견적도 복제된다")
    @EnumSource(Status.class)
    @DisplayName("원본 상태를 가리지 않는다 — 막는 축은 종결 Deal 하나뿐이다 (Q-25)")
    void 모든_상태에서_복제된다(Status status) {
        Quote copy = 원본(status).cloneAsDraft("Q-2610-001", 새_유효기간);

        assertThat(copy.getStatus()).isEqualTo(Status.DRAFT);
    }

    @Test
    @DisplayName("내용은 베낀다 — 항목·부가세 모드·조건 문구")
    void 내용을_베낀다() {
        Quote origin = 원본(Status.REJECTED);

        Quote copy = origin.cloneAsDraft("Q-2610-001", 새_유효기간);

        assertThat(copy.getVatMode()).isEqualTo(VatMode.INCLUDED);
        assertThat(copy.getTerms()).isEqualTo("결제는 납품 후 30일 이내");
        assertThat(copy.getItems()).extracting(QuoteItem::getName).containsExactly("책상", "의자");
        assertThat(copy.getItems()).extracting(QuoteItem::getSortOrder).containsExactly(0, 1);
        assertThat(copy.getItems()).extracting(QuoteItem::getCatalogPriceAtCreation)
                .containsExactly(null, 85_000L);
        assertThat(copy.getTotalAmount()).isEqualTo(origin.getTotalAmount());
    }

    /**
     * 발송·응답은 <b>원본에게 일어난 일</b>이라 새 견적의 사실이 아니다.
     * 함께 베끼면 한 번도 보낸 적 없는 견적이 "발송됨"으로 태어난다.
     */
    @Test
    @DisplayName("이력은 베끼지 않는다 — 발송·열람·응답·반려 사유·응답자")
    void 이력은_베끼지_않는다() {
        Quote copy = 원본(Status.REJECTED).cloneAsDraft("Q-2610-001", 새_유효기간);

        assertThat(copy.getSentAt()).isNull();
        assertThat(copy.getFirstViewedAt()).isNull();
        assertThat(copy.getRespondedAt()).isNull();
        assertThat(copy.getRejectReason()).isNull();
        assertThat(copy.getResponderName()).isNull();
    }

    @Test
    @DisplayName("번호와 유효기간은 새로 받는다 — 원본 것은 이미 낡았을 수 있다")
    void 번호와_유효기간은_새것이다() {
        Quote origin = 원본(Status.EXPIRED);

        Quote copy = origin.cloneAsDraft("Q-2610-001", 새_유효기간);

        assertThat(copy.getQuoteNo()).isEqualTo("Q-2610-001");
        assertThat(copy.getValidUntil()).isEqualTo(새_유효기간);
        assertThat(origin.getQuoteNo()).isEqualTo("Q-2609-001");   // 원본은 그대로
    }

    @Test
    @DisplayName("계보는 직전 원본 한 단계다 — 복제의 복제는 중간 견적을 가리킨다")
    void 계보는_한_단계다() {
        Quote origin = 원본(Status.REJECTED);
        Quote first = origin.cloneAsDraft("Q-2610-001", 새_유효기간);
        ReflectionTestUtils.setField(first, "id", UUID.randomUUID());

        Quote second = first.cloneAsDraft("Q-2610-002", 새_유효기간);

        assertThat(first.getClonedFromQuoteId()).isEqualTo(origin.getId());
        assertThat(second.getClonedFromQuoteId()).isEqualTo(first.getId());
    }

    /**
     * 항목을 얕게 복사하면 새 견적을 고칠 때 원본 항목까지 바뀐다 —
     * 발송 견적 불변(QT-16)이 조용히 깨지는 경로다.
     */
    @Test
    @DisplayName("항목은 새 인스턴스다 — 복제본을 고쳐도 원본이 흔들리지 않는다 (QT-16)")
    void 항목은_깊은_복사다() {
        Quote origin = 원본(Status.SENT);

        Quote copy = origin.cloneAsDraft("Q-2610-001", 새_유효기간);
        copy.replaceItems(List.of(QuoteItem.of(null, "책장", "EA", 1, 50_000L, null, 0)));

        assertThat(origin.getItems()).extracting(QuoteItem::getName).containsExactly("책상", "의자");
        assertThat(copy.getItems()).extracting(QuoteItem::getName).containsExactly("책장");
    }

    @Test
    @DisplayName("원본은 어떤 것도 바뀌지 않는다 — 복제는 읽기다 (전이표 §6)")
    void 원본은_그대로다() {
        Quote origin = 원본(Status.REJECTED);

        origin.cloneAsDraft("Q-2610-001", 새_유효기간);

        assertThat(origin.getStatus()).isEqualTo(Status.REJECTED);
        assertThat(origin.getSentAt()).isNotNull();
        assertThat(origin.getClonedFromQuoteId()).isNull();
    }
}
