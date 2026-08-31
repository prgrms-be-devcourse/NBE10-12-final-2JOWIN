package com.twojo.auth.repository;

import com.twojo.auth.entity.ActorType;
import com.twojo.auth.entity.LoginAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** login_attempt 접근 — 잠금 판정 전용 (AU-09). */
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    /**
     * 최근 시도 N건(최신순) — 잠금 판정의 유일한 조회.
     *
     * <p>"마지막 성공 이후 연속 실패 5회 이상"은 최근 5건이 전부 실패인 것과 동치라,
     * 마지막 성공 시각을 따로 구하지 않는다 (docs/06 "DB로 못 막는 것").
     * 건수는 Pageable로 받는다 — 정책 수치는 LoginAttemptService가 갖는다.
     *
     * <p>actorType으로 갈라 조회한다: member와 platform_admin은 이메일이 겹칠 수 있어
     * 섞으면 관리자의 실패가 구성원을 잠근다 (Q-30).
     */
    List<LoginAttempt> findByEmailAndActorTypeOrderByAttemptedAtDesc(
            String email, ActorType actorType, Pageable pageable);
}
