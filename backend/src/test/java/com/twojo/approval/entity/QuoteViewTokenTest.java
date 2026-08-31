package com.twojo.approval.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.approval.entity.QuoteViewToken.ExpiredReason;
import com.twojo.approval.entity.QuoteViewToken.Status;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuoteViewTokenTest {

    private static final Instant FUTURE = Instant.now().plusSeconds(3600);
    private static final Instant PAST = Instant.now().minusSeconds(3600);

    private static QuoteViewToken activeToken(Instant expiresAt) {
        return QuoteViewToken.issue(UUID.randomUUID(), UUID.randomUUID(), "hash", expiresAt);
    }

    @Test
    @DisplayName("issue()로 만든 링크는 ACTIVE 상태이고 만료 사유는 비어 있다 (Q-40)")
    void issue_active() {
        QuoteViewToken token = activeToken(FUTURE);

        assertThat(token.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(token.getExpiredReason()).isNull();
    }

    @Test
    @DisplayName("respond()는 ACTIVE 링크를 RESPONDED로 전이한다 (AP-11)")
    void respond_success() {
        QuoteViewToken token = activeToken(FUTURE);

        token.respond();

        assertThat(token.getStatus()).isEqualTo(Status.RESPONDED);
    }

    @Test
    @DisplayName("이미 RESPONDED인 링크에 respond()하면 IllegalStateException을 던진다 (AP-11)")
    void respond_alreadyResponded() {
        QuoteViewToken token = activeToken(FUTURE);
        token.respond();

        assertThatThrownBy(token::respond).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("EXPIRED 링크에 respond()하면 IllegalStateException을 던진다")
    void respond_expired() {
        QuoteViewToken token = activeToken(FUTURE);
        token.expire(ExpiredReason.WITHDRAWN);

        assertThatThrownBy(token::respond).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("expire()는 ACTIVE 링크를 EXPIRED로 전이하고 사유를 기록한다 (전이표 §7)")
    void expire_success() {
        QuoteViewToken token = activeToken(FUTURE);

        token.expire(ExpiredReason.TIME);

        assertThat(token.getStatus()).isEqualTo(Status.EXPIRED);
        assertThat(token.getExpiredReason()).isEqualTo(ExpiredReason.TIME);
    }

    @Test
    @DisplayName("이미 EXPIRED면 expire()는 최초 사유를 보존하고 아무것도 하지 않는다 (멱등, C↔D 계약)")
    void expire_idempotentWhenExpired() {
        QuoteViewToken token = activeToken(FUTURE);
        token.expire(ExpiredReason.WITHDRAWN);

        token.expire(ExpiredReason.TIME);

        assertThat(token.getStatus()).isEqualTo(Status.EXPIRED);
        assertThat(token.getExpiredReason()).isEqualTo(ExpiredReason.WITHDRAWN);
    }

    @Test
    @DisplayName("RESPONDED 링크에 expire()해도 상태·사유가 바뀌지 않는다")
    void expire_noOpWhenResponded() {
        QuoteViewToken token = activeToken(FUTURE);
        token.respond();

        token.expire(ExpiredReason.DEAL_LOST);

        assertThat(token.getStatus()).isEqualTo(Status.RESPONDED);
        assertThat(token.getExpiredReason()).isNull();
    }

    @Test
    @DisplayName("ACTIVE이고 유효기간 전이면 isViewable은 true다")
    void isViewable_activeBeforeExpiry() {
        assertThat(activeToken(FUTURE).isViewable(Instant.now())).isTrue();
    }

    @Test
    @DisplayName("RESPONDED여도 유효기간 전이면 열람은 허용한다 (재응답만 차단, AP-11)")
    void isViewable_respondedBeforeExpiry() {
        QuoteViewToken token = activeToken(FUTURE);
        token.respond();

        assertThat(token.isViewable(Instant.now())).isTrue();
    }

    @Test
    @DisplayName("EXPIRED면 isViewable은 false다")
    void isViewable_expired() {
        QuoteViewToken token = activeToken(FUTURE);
        token.expire(ExpiredReason.TIME);

        assertThat(token.isViewable(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("상태가 ACTIVE여도 유효기간이 지났으면 isViewable은 false다")
    void isViewable_activeAfterExpiry() {
        assertThat(activeToken(PAST).isViewable(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("ACTIVE이고 유효기간 전일 때만 isRespondable은 true다")
    void isRespondable_activeBeforeExpiry() {
        assertThat(activeToken(FUTURE).isRespondable(Instant.now())).isTrue();
    }

    @Test
    @DisplayName("RESPONDED면 isRespondable은 false다")
    void isRespondable_responded() {
        QuoteViewToken token = activeToken(FUTURE);
        token.respond();

        assertThat(token.isRespondable(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("ACTIVE여도 유효기간이 지났으면 isRespondable은 false다")
    void isRespondable_activeAfterExpiry() {
        assertThat(activeToken(PAST).isRespondable(Instant.now())).isFalse();
    }
}
