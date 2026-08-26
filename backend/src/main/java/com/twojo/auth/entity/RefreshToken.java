package com.twojo.auth.entity;

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
 * 세션 원본 — "즉시 차단"의 실체 (AU-02·10). 다중 기기 허용 (Q-28).
 * 회전 = 기존 행 REVOKED(ROTATED) + 새 행. EXPIRED 상태 없음 — expires_at 비교 판정.
 * member_id·platform_admin_id 중 정확히 하나 (DB CHECK).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    public enum Status { ACTIVE, REVOKED }

    public enum RevokedReason {
        ROTATED, LOGOUT, PASSWORD_CHANGED, MEMBER_DEACTIVATED, COMPANY_SUSPENDED, REUSE_DETECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private ActorType actorType;

    private UUID memberId;

    private UUID platformAdminId;

    private String tokenHash;   // 해시만 저장 — raw는 발급 시점 메모리에만

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private RevokedReason revokedReason;

    private Instant expiresAt;   // 미유지 12h / 유지 14d (Q-32)

    private Instant lastUsedAt;

    private Instant revokedAt;
}
