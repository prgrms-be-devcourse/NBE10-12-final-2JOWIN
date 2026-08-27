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
 *
 * <p>상태 전이는 전부 이 클래스의 메서드로만 일어난다 — 표에 없는 전이를 코드로 차단한다
 * (docs/05-state-transitions.md §9 · docs/14-tech-stack.md §1.2).
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

    /**
     * 로그인 성공 시 발급 — 전이표 §9 "(없음) → 활성(ACTIVE)".
     * 수명은 호출자가 계산해 넘긴다 (Q-32: 유지 미선택 12h / 선택 14d).
     */
    public static RefreshToken issueForMember(UUID memberId, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.actorType = ActorType.MEMBER;
        token.memberId = memberId;
        token.tokenHash = tokenHash;
        token.status = Status.ACTIVE;
        token.expiresAt = expiresAt;
        return token;
    }

    /**
     * 폐기 — 전이표 §9의 활성 → 폐기 전이 전부
     * (회전·로그아웃·비밀번호 변경·구성원 비활성화·회사 정지·재사용 감지).
     *
     * <p>이미 폐기된 행은 사유를 덮지 않는다. 최초 폐기 사유가 감사 근거이기 때문이다.
     */
    public void revoke(RevokedReason reason, Instant now) {
        if (status != Status.ACTIVE) {
            return;
        }
        this.status = Status.REVOKED;
        this.revokedReason = reason;
        this.revokedAt = now;
    }

    /**
     * 재발급에 쓸 수 있는가 — 만료됨 상태를 두지 않으므로 expires_at 비교로 판정한다 (전이표 §9).
     */
    public boolean isUsableAt(Instant now) {
        return status == Status.ACTIVE && expiresAt.isAfter(now);
    }

    /**
     * 회전된 토큰의 재사용인가 — 폐기 사유가 ROTATED인 행이 다시 제시되면 침해 신호다.
     * 로그아웃·비밀번호 변경으로 폐기된 행은 그냥 낡은 토큰이므로 감지 대상이 아니다 (전이표 §9).
     */
    public boolean isRotated() {
        return status == Status.REVOKED && revokedReason == RevokedReason.ROTATED;
    }

    /** 회전 성공 시 직전 사용 시각 기록 — 운영 지표용이며 판정에는 쓰지 않는다. */
    public void markUsed(Instant now) {
        this.lastUsedAt = now;
    }
}
