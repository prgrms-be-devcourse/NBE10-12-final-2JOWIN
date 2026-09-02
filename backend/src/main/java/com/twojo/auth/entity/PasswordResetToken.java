package com.twojo.auth.entity;

import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
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
import lombok.RequiredArgsConstructor;

/**
 * 비밀번호 재설정 토큰 — RESET 30분 / INITIAL_SETUP 7일 (Q-33·34).
 * 구성원당 활성 1개 (부분 유니크). 사용 시 해당 구성원 refresh_token 전 행 폐기.
 *
 * <p>상태 전이는 전부 이 클래스의 메서드로만 일어난다 — 표에 없는 전이를 코드로 차단한다
 * (docs/05-state-transitions.md §10 · docs/14-tech-stack.md §1.2).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends BaseTimeEntity {

    /**
     * 목적별 수명 (Q-34) — RESET 30분 / INITIAL_SETUP 7일.
     *
     * <p>수명을 상수에 붙여 둔다. 발급 지점이 둘(재설정 요청 AU-05 · 가입 승인 ON-07)이라
     * 서비스마다 분기를 복제하면 한쪽만 고치는 사고가 난다.
     */
    @Getter
    @RequiredArgsConstructor
    public enum Purpose {
        RESET(Duration.ofMinutes(30)),
        INITIAL_SETUP(Duration.ofDays(7));

        private final Duration ttl;
    }

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

    /**
     * 발급 — 05 §10의 (없음) -> 활성(ACTIVE) 전이.
     *
     * <p>원문이 아니라 해시를 받는다. 원문은 메일 링크에 실려야 해서 호출자가 계속 쥐고 있고,
     * DB에는 해시만 남는다 (14 §2-1 · SecureTokenFactory).
     *
     * <p>기존 활성 행의 만료는 여기서 하지 않는다 — 다른 행을 건드리는 일이라 서비스의 몫이다.
     */
    public static PasswordResetToken issue(UUID memberId, Purpose purpose, String tokenHash, Instant now) {
        PasswordResetToken token = new PasswordResetToken();
        token.memberId = memberId;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.status = Status.ACTIVE;
        token.expiresAt = now.plus(purpose.getTtl());
        return token;
    }

    /**
     * 재설정 완료 — 05 §10의 활성(ACTIVE) -> 사용됨(USED) 전이.
     * refresh_token 폐기는 전이표의 '효과'이지 이 행의 상태가 아니다 — 서비스가 수행한다.
     */
    public void use(Instant now) {
        requireUsableAt(now);
        this.status = Status.USED;
        this.usedAt = now;
    }

    /**
     * 만료 — 05 §10의 활성(ACTIVE) -> 만료됨(EXPIRED) 전이. 재요청 시 기존 행에 쓴다.
     *
     * <p>이미 종결된 행은 덮지 않는다. USED를 EXPIRED로 바꾸면 그 링크를 실제로 썼는지가 사라진다.
     */
    public void expire() {
        if (status != Status.ACTIVE) {
            return;
        }
        this.status = Status.EXPIRED;
    }

    /**
     * 쓸 수 있는가 — 상태와 수명을 함께 본다.
     *
     * <p>수명 경과 -> EXPIRED 전이는 배치 소유인데(Q-34) 그 배치가 아직 없다.
     * 상태만 보면 배치가 도는 사이에 만료된 링크가 통과한다.
     */
    public boolean isUsableAt(Instant now) {
        return status == Status.ACTIVE && expiresAt.isAfter(now);
    }

    private void requireUsableAt(Instant now) {
        if (!isUsableAt(now)) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_NOT_ACTIVE);   // 05 §10
        }
    }
}
