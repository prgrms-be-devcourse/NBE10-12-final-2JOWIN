package com.twojo.member.entity;

import com.twojo.boundary.Role;
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
}
