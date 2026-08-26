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
 * 로그인 시도 — 잠금 판정 (AU-09: 마지막 성공 이후 연속 실패 5회 + 마지막 실패로부터 10분 차단).
 * 미가입 이메일도 기록 (SC-09 — 잠김 여부로 계정 존재 노출 방지). company_id 없음(테넌트 격리 예외).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String email;   // lower() 정규화는 앱 책임

    @Enumerated(EnumType.STRING)
    private ActorType actorType;

    private UUID memberId;   // null = 미가입 또는 관리자

    private boolean success;

    private String ipAddress;

    private Instant attemptedAt;
}
