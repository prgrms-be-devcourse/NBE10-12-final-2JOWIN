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

    /** 비활성 구성원은 로그인·재발급 모두 차단 — 권한 이전에 인증에서 막는다 (MB-10). */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /**
     * 비밀번호가 설정돼 있는가 — 가입 승인 직후 계정은 password_hash가 NULL이다 (Q-33).
     * 미설정 계정에 별도 상태를 두지 않으므로 로그인 시도는 자연히 LOGIN_FAILED로 떨어진다 (SC-09).
     */
    public boolean hasPassword() {
        return passwordHash != null;
    }

    /**
     * 비밀번호 교체 — 변경(AU-04)·최초 설정(AU-05) 공용.
     *
     * <p>password_changed_at은 "이 시각 이후 발급된 토큰만 유효"의 기준이다 (06).
     * 최초 설정이면 이 호출로 passwordHash가 NULL에서 벗어나 hasPassword()가 true가 된다 (Q-33).
     */
    public void changePassword(String newPasswordHash, Instant changedAt) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = changedAt;
    }
}
