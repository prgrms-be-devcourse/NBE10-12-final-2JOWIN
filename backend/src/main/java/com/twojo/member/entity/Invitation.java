package com.twojo.member.entity;

import com.twojo.boundary.Role;
import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 초대 — 7일 만료 (MB-04). 재발송 = 기존 행 EXPIRED(RESENT) + 새 행 (Q-31). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation extends BaseTimeEntity {

    public enum Status { PENDING, ACCEPTED, CANCELED, EXPIRED }

    public enum ExpiredReason { TIME, RESENT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private UUID invitedByMemberId;

    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;   // MB-02 — 초대 시 역할 지정 필수

    private String tokenHash;   // 원문 미저장

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant expiresAt;

    private Instant acceptedAt;

    private Instant canceledAt;

    private Instant expiredAt;

    @Enumerated(EnumType.STRING)
    private ExpiredReason expiredReason;

    /** 7일 유효 (MB-04). */
    private static final Duration LIFETIME = Duration.ofDays(7);

    /** 발송 — 새 대기 행. 같은 회사·이메일의 대기 행이 이미 없는지는 호출 전에 확인된다. */
    public static Invitation issue(UUID companyId, UUID invitedByMemberId, String email,
                                   Role role, String tokenHash, Instant now) {
        Invitation invitation = new Invitation();
        invitation.companyId = companyId;
        invitation.invitedByMemberId = invitedByMemberId;
        invitation.email = email;
        invitation.role = role;
        invitation.tokenHash = tokenHash;
        invitation.status = Status.PENDING;
        invitation.expiresAt = now.plus(LIFETIME);
        return invitation;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    /** 기한이 지났는가 — 상태는 아직 PENDING일 수 있다. 넘기는 것은 호출자가 한다. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * 수락 (MB-03) · 취소 (MB-05) · 만료 (MB-04·06).
     *
     * <p>셋 다 대기 상태를 검사하지 않는다. 검사를 여기 두면 서비스와 두 곳에서 같은 판정을
     * 하게 되고, 어느 쪽 예외가 나가는지가 호출 순서에 따라 달라진다.
     */
    public void accept(Instant now) {
        this.status = Status.ACCEPTED;
        this.acceptedAt = now;
    }

    public void cancel(Instant now) {
        this.status = Status.CANCELED;
        this.canceledAt = now;
    }

    public void expire(ExpiredReason reason, Instant now) {
        this.status = Status.EXPIRED;
        this.expiredReason = reason;
        this.expiredAt = now;
    }
}
