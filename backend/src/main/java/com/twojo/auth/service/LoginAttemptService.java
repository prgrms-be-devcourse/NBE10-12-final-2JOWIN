package com.twojo.auth.service;

import com.twojo.auth.entity.ActorType;
import com.twojo.auth.entity.LoginAttempt;
import com.twojo.auth.repository.LoginAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 잠금 판정과 시도 기록 (AU-06·09).
 *
 * <p>정책 수치는 여기가 소유한다 — 리포지토리는 "최근 N건"만 알고 N이 몇인지는 모른다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    /** AU-09 — 마지막 성공 이후 이 횟수만큼 연속 실패하면 잠근다. */
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    /** AU-09 — 잠금 지속 시간. 판정 윈도우가 아니다 (06:461 v1.6.1 정정). */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final LoginAttemptRepository loginAttemptRepository;

    /**
     * 잠겨 있는가 — 비밀번호를 확인하기 전에 먼저 묻는다.
     * 미가입 이메일도 같은 경로를 탄다: 잠금 여부로 계정 존재가 드러나면 안 된다 (SC-09).
     */
    @Transactional(readOnly = true)
    public boolean isLocked(String email, ActorType actorType, Instant now) {
        List<LoginAttempt> recent = loginAttemptRepository
                .findByEmailAndActorTypeOrderByAttemptedAtDesc(
                        LoginAttempt.normalize(email), actorType,
                        PageRequest.of(0, MAX_CONSECUTIVE_FAILURES));

        if (recent.size() < MAX_CONSECUTIVE_FAILURES) {
            return false;
        }
        if (recent.stream().anyMatch(LoginAttempt::isSuccess)) {
            return false;
        }
        return recent.get(0).getAttemptedAt().isAfter(now.minus(LOCK_DURATION));
    }

    /** 성공 기록 — 이 한 건이 이전 실패들의 연속을 끊는다 (AU-09). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String email, ActorType actorType, UUID memberId,
                              String ipAddress, Instant now) {
        loginAttemptRepository.save(
                LoginAttempt.success(email, actorType, memberId, ipAddress, now));
    }

    /**
     * 실패 기록 — 반드시 별도 트랜잭션이어야 한다.
     * 호출자가 곧바로 LOGIN_FAILED를 던지므로, 같은 트랜잭션이면 이 기록도 롤백되어
     * 몇 번을 틀려도 잠금이 걸리지 않는다.
     *
     * <p>memberId는 미가입 이메일에서 null이다 — 그래도 기록한다 (SC-09).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, ActorType actorType, UUID memberId,
                              String ipAddress, Instant now) {
        loginAttemptRepository.save(
                LoginAttempt.failure(email, actorType, memberId, ipAddress, now));
    }
}
