package com.twojo.approval.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
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
}
