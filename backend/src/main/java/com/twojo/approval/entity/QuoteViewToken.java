package com.twojo.approval.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 열람 링크 — 토큰이 곧 인증 (SC-07~09). 견적당 활성 1개 (AP-03, 부분 유니크).
 * raw 토큰은 메일 렌더링 시점 메모리에만 — DB엔 해시만.
 * 발급은 C의 발송 트랜잭션 안에서 동기 (Q-40).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteViewToken extends BaseTimeEntity {

    public enum Status { ACTIVE, RESPONDED, EXPIRED }

    public enum ExpiredReason { TIME, MANUAL, WITHDRAWN, RESENT, DEAL_LOST }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID quoteId;

    private UUID recipientContactId;   // 수신인 (AP-13 재발송 시 새 행)

    private String tokenHash;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private ExpiredReason expiredReason;

    private Instant expiresAt;   // valid_until 당일 23:59:59 KST

    /** 발송·재발송 시 발급 — ACTIVE 상태로 생성 (Q-40, AP-13). */
    public static QuoteViewToken issue(UUID quoteId, UUID recipientContactId,
                                       String tokenHash, Instant expiresAt) {
        QuoteViewToken token = new QuoteViewToken();
        token.quoteId = Objects.requireNonNull(quoteId, "quoteId");
        token.recipientContactId = Objects.requireNonNull(recipientContactId, "recipientContactId");
        token.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        token.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        token.status = Status.ACTIVE;
        return token;
    }

    /**
     * 고객 응답 시 소진 — ACTIVE → RESPONDED (AP-11).
     * 서비스가 만료·응답완료·회사정지를 먼저 검증하므로, 여기서는 오작동 방어선일 뿐이다.
     */
    public void respond() {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("ACTIVE 링크만 응답 완료로 소진할 수 있습니다: " + status);
        }
        this.status = Status.RESPONDED;
    }

    /**
     * 활성 링크 만료 — ACTIVE → EXPIRED + 사유 (전이표 §7).
     * 멱등: 이미 EXPIRED·RESPONDED면 아무 것도 하지 않는다(최초 사유 보존) — C↔D 계약.
     */
    public void expire(ExpiredReason reason) {
        if (status != Status.ACTIVE) {
            return;
        }
        this.status = Status.EXPIRED;
        this.expiredReason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * 열람 가능 여부 — EXPIRED가 아니고 유효기간도 안 지났을 때.
     * RESPONDED 링크도 열람은 허용한다(재응답만 차단, AP-11). {@code status}는 만료 배치 전까지
     * ACTIVE로 남을 수 있어 {@code expiresAt}로 읽기 시점 시간 만료를 함께 본다.
     */
    public boolean isViewable(Instant now) {
        return status != Status.EXPIRED && expiresAt.isAfter(now);
    }

    /** 응답(승인·반려) 가능 여부 — ACTIVE이고 유효기간도 안 지났을 때만. */
    public boolean isRespondable(Instant now) {
        return status == Status.ACTIVE && expiresAt.isAfter(now);
    }
}
