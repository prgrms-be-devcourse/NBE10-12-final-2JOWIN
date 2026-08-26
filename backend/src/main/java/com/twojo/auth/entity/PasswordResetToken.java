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
 * 비밀번호 재설정 토큰 — RESET 30분 / INITIAL_SETUP 7일 (Q-33·34).
 * 구성원당 활성 1개 (부분 유니크). 사용 시 해당 구성원 refresh_token 전 행 폐기.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends BaseTimeEntity {

    public enum Purpose { RESET, INITIAL_SETUP }

    public enum Status { ACTIVE, USED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID memberId;

    @Enumerated(EnumType.STRING)
    private Purpose purpose;

    private String tokenHash;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant expiresAt;

    private Instant usedAt;
}
