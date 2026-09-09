package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.twojo.approval.entity.QuoteViewToken;
import com.twojo.approval.repository.QuoteViewTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ViewTokenQueryImpl} — 판정 로직이 없어 리포지토리 결과가 그대로 전달되는지만 검증한다
 * (CU-14 존재 판정, NT-12 토큰&rarr;견적 되짚기).
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewTokenQueryImplTest {

    @Mock
    private QuoteViewTokenRepository quoteViewTokenRepository;

    @InjectMocks
    private ViewTokenQueryImpl viewTokenQuery;

    @Test
    @DisplayName("수신인 지정 이력이 있으면 리포지토리 결과를 그대로 true로 반환한다")
    void 발송_이력이_있으면_true를_반환한다() {
        UUID contactId = UUID.randomUUID();
        given(quoteViewTokenRepository.existsByRecipientContactId(contactId)).willReturn(true);

        assertThat(viewTokenQuery.existsForContact(contactId)).isTrue();
    }

    @Test
    @DisplayName("수신인 지정 이력이 없으면 false를 반환한다")
    void 발송_이력이_없으면_false를_반환한다() {
        UUID contactId = UUID.randomUUID();
        given(quoteViewTokenRepository.existsByRecipientContactId(contactId)).willReturn(false);

        assertThat(viewTokenQuery.existsForContact(contactId)).isFalse();
    }

    @Test
    @DisplayName("quoteIdOf — 토큰 id로 그 링크가 가리키는 견적 id를 반환한다")
    void quoteIdOf_토큰의_견적id를_반환한다() {
        UUID tokenId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        QuoteViewToken token = QuoteViewToken.issue(
                quoteId, UUID.randomUUID(), "hash", Instant.parse("2026-09-30T14:59:59Z"));
        given(quoteViewTokenRepository.findById(tokenId)).willReturn(Optional.of(token));

        assertThat(viewTokenQuery.quoteIdOf(tokenId)).contains(quoteId);
    }

    @Test
    @DisplayName("quoteIdOf — 토큰 행이 없으면 Optional.empty를 반환한다 (예외 아님)")
    void quoteIdOf_행이_없으면_empty를_반환한다() {
        UUID tokenId = UUID.randomUUID();
        given(quoteViewTokenRepository.findById(tokenId)).willReturn(Optional.empty());

        assertThat(viewTokenQuery.quoteIdOf(tokenId)).isEmpty();
    }
}
