package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

import com.twojo.approval.entity.QuoteViewToken;
import com.twojo.approval.repository.QuoteViewTokenRepository;
import com.twojo.boundary.ViewTokenCommand;
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
 * {@link ViewTokenCommandImpl#expire} — 조회된 ACTIVE 링크에 엔티티 전이를 위임하는지,
 * 계약 사유를 엔티티 사유로 맞게 매핑하는지, ACTIVE 링크가 없을 때 무동작인지 검증한다.
 * 전이 규칙 자체(멱등·최초 사유 보존)는 {@code QuoteViewTokenTest}가 커버한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewTokenCommandImplTest {

    private static final UUID QUOTE_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Mock
    private QuoteViewTokenRepository quoteViewTokenRepository;

    @InjectMocks
    private ViewTokenCommandImpl viewTokenCommand;

    private static QuoteViewToken activeToken() {
        return QuoteViewToken.issue(QUOTE_ID, UUID.randomUUID(), "hash", Instant.now().plusSeconds(3600));
    }

    @Test
    @DisplayName("ACTIVE 링크가 있으면 EXPIRED로 전이하고 사유를 기록한다")
    void ACTIVE_링크를_EXPIRED로_전이한다() {
        QuoteViewToken token = activeToken();
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.of(token));

        viewTokenCommand.expire(QUOTE_ID, ViewTokenCommand.ExpiredReason.WITHDRAWN);

        assertThat(token.getStatus()).isEqualTo(QuoteViewToken.Status.EXPIRED);
        assertThat(token.getExpiredReason()).isEqualTo(QuoteViewToken.ExpiredReason.WITHDRAWN);
    }

    @Test
    @DisplayName("계약 사유를 이름이 같은 엔티티 사유로 매핑한다 — 하드코딩이 아니다")
    void 계약_사유를_엔티티_사유로_매핑한다() {
        QuoteViewToken token = activeToken();
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.of(token));

        viewTokenCommand.expire(QUOTE_ID, ViewTokenCommand.ExpiredReason.DEAL_LOST);

        assertThat(token.getExpiredReason()).isEqualTo(QuoteViewToken.ExpiredReason.DEAL_LOST);
    }

    @Test
    @DisplayName("ACTIVE 링크가 없으면 예외 없이 무동작한다 (멱등)")
    void ACTIVE_링크가_없으면_무동작한다() {
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.empty());

        assertThatCode(() -> viewTokenCommand.expire(QUOTE_ID, ViewTokenCommand.ExpiredReason.TIME))
                .doesNotThrowAnyException();
    }
}
