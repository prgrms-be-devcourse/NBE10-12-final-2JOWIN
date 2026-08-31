package com.twojo.auth.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 시도 — 잠금 판정 (AU-09: 마지막 성공 이후 연속 실패 5회 + 마지막 실패로부터 10분 차단).
 * 미가입 이메일도 기록 (SC-09 — 잠김 여부로 계정 존재 노출 방지). company_id 없음(테넌트 격리 예외).
 *
 * <p>상태 전이가 없는 append-only 기록이다 — 생성 팩토리만 두고 수정 메서드는 두지 않는다.
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

    /**
     * 성공 기록 — 잠금 판정이 "마지막 성공 이후의 연속 실패"를 세므로 성공도 남겨야 한다 (AU-09).
     * 성공이 기록되지 않으면 과거 실패가 영원히 누적되어 정상 사용자가 잠긴다.
     */
    public static LoginAttempt success(String email, ActorType actorType, UUID memberId,
                                       String ipAddress, Instant attemptedAt) {
        return of(email, actorType, memberId, true, ipAddress, attemptedAt);
    }

    /**
     * 실패 기록 — 미가입 이메일도 남긴다. 가입된 계정만 기록하면
     * "잠기는가"로 계정 존재 여부가 드러난다 (SC-09 인증 확장).
     */
    public static LoginAttempt failure(String email, ActorType actorType, UUID memberId,
                                       String ipAddress, Instant attemptedAt) {
        return of(email, actorType, memberId, false, ipAddress, attemptedAt);
    }

    private static LoginAttempt of(String email, ActorType actorType, UUID memberId,
                                   boolean success, String ipAddress, Instant attemptedAt) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.email = normalize(email);
        attempt.actorType = actorType;
        attempt.memberId = memberId;
        attempt.success = success;
        attempt.ipAddress = ipAddress;
        attempt.attemptedAt = attemptedAt;
        return attempt;
    }

    /**
     * 이메일 정규화 — 대소문자를 다르게 넣어 잠금을 우회하지 못하게 한다.
     * 조회 쪽도 같은 규칙으로 정규화해야 판정이 성립한다.
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
