package com.twojo.quote.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.quote.entity.Quote.Status;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 기간 만료 전이 (Q-37) — 전이표 §6의 "발송됨·열람됨 → 기간 만료 · 주체 시스템" 행.
 *
 * <p><b>다른 전이와 성격이 다르다.</b> 회수·승인·반려는 상태가 안 맞으면 예외를 던지지만
 * 만료는 {@code false}를 돌려준다 — 호출자가 사람이 아니라 배치이기 때문이다.
 * 같은 날 두 번 돌거나, 배치 조회 직후 담당자가 회수했거나, 고객이 승인한 경우가
 * 정상적으로 생긴다.
 */
class QuoteExpiryTransitionTest {

    private static Quote quoteAt(Status status) {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID(), "Q-2609-001",
                LocalDate.of(2026, 9, 10));
        ReflectionTestUtils.setField(quote, "status", status);
        return quote;
    }

    @ParameterizedTest(name = "{0}는 기간 만료로 닫힌다")
    @EnumSource(value = Status.class, names = {"SENT", "VIEWED"})
    @DisplayName("발송됨·열람됨만 만료된다 — 아직 답을 기다리던 견적이다")
    void 응답_대기는_만료된다(Status status) {
        Quote quote = quoteAt(status);

        assertThat(quote.expire()).isTrue();
        assertThat(quote.getStatus()).isEqualTo(Status.EXPIRED);
    }

    /**
     * <b>표에 없는 전이는 막힌다</b>(전이표 §6 원칙). 다만 막는 방식이 예외가 아니라
     * {@code false}다 — 배치가 부르는 자리라 "이미 닫혀 있음"이 오류가 아니기 때문이다.
     */
    @ParameterizedTest(name = "{0}는 만료 대상이 아니다 — 상태가 그대로다")
    @EnumSource(value = Status.class, names = {"DRAFT", "APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED"})
    @DisplayName("작성 중·종결 상태는 만료되지 않는다 — 예외가 아니라 false다")
    void 대상이_아니면_false(Status status) {
        Quote quote = quoteAt(status);

        assertThat(quote.expire()).isFalse();
        assertThat(quote.getStatus()).isEqualTo(status);   // 건드리지 않는다
    }

    @Test
    @DisplayName("두 번 불러도 안전하다 — 배치 재실행이 상태를 망가뜨리지 않는다")
    void 멱등() {
        Quote quote = quoteAt(Status.SENT);

        assertThat(quote.expire()).isTrue();
        assertThat(quote.expire()).isFalse();
        assertThat(quote.getStatus()).isEqualTo(Status.EXPIRED);
    }
}
