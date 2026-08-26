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

/**
 * 구성원 — 삭제 없음, 비활성화만 (MB). 이메일은 lower(email) 전역 유일 (Q-14).
 * password_hash NULL = 가입 승인 직후 미설정 계정 — 로그인은 자연히 LOGIN_FAILED (Q-33).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    public enum Status { ACTIVE, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private String email;

    private String passwordHash;

    private String name;

    private String phone;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant passwordChangedAt;   // AU-04·05 — 이 시각 이후 발급 토큰만 유효
}
