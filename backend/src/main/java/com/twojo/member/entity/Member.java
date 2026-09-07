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

    /**
     * 초대 수락으로 생기는 계정 (MB-03) — 처음부터 활성이다.
     *
     * <p>역할은 초대에 박혀 있던 값이다. 수락자가 고르지 않는다 (MB-02).
     * passwordChangedAt은 이 시각이 최초 설정 시점이라 지금으로 찍는다.
     */
    public static Member invited(UUID companyId, String email, String passwordHash,
                                 String name, Role role, Instant now) {
        Member member = new Member();
        member.companyId = companyId;
        member.email = email;
        member.passwordHash = passwordHash;
        member.name = name;
        member.role = role;
        member.status = Status.ACTIVE;
        member.passwordChangedAt = now;
        return member;
    }

    /**
     * 가입 승인으로 생기는 회사의 첫 기업 관리자 (ON-07).
     *
     * <p>{@code passwordHash}·{@code passwordChangedAt}이 둘 다 NULL이다 — 아직 설정 전이고
     * (Q-33), 최초 설정이 곧 첫 변경 시점이라 그때 함께 찍힌다. {@link #invited}가 지금으로
     * 찍는 것은 그쪽은 수락하며 비밀번호를 정하기 때문이다.
     *
     * <p>{@code passwordChangedAt}이 NULL인 동안 access token은 발급되지 않는다 —
     * 비밀번호가 없으면 로그인 자체를 통과하지 못한다.
     *
     * <p>이름은 신청서의 신청자 이름이다 (08 v1.6.11). 전화번호는 받지 않는다 —
     * 신청서에 없고, 본인이 프로필 수정(AU-07)으로 채운다.
     */
    public static Member companyAdmin(UUID companyId, String email, String name) {
        Member member = new Member();
        member.companyId = companyId;
        member.email = email;
        member.name = name;
        member.role = Role.COMPANY_ADMIN;
        member.status = Status.ACTIVE;
        return member;
    }

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

    /**
     * 프로필 수정 (AU-07) — 이름과 연락처만.
     *
     * <p>연락처는 null을 그대로 받는다. 비울 수 있는 값이라 "안 보냄"과 "비움"을 구별하지 않는다.
     */
    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    /**
     * 역할 변경 (MB-08).
     *
     * <p>회사에 활성 관리자가 남는지는 호출 전에 확인된다 (MB-11). 엔티티는 자기 회사에
     * 다른 관리자가 몇 명인지 알 수 없다 — 그 판단은 서비스의 몫이다.
     */
    public void changeRole(Role newRole) {
        this.role = newRole;
    }
}
