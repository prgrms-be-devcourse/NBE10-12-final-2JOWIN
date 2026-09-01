package com.twojo.auth.service;

import com.twojo.auth.entity.RefreshToken;
import com.twojo.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 폐기 공통 로직 — 11 §2 "A가 책임지는 공통 기반".
 *
 * <p>전이표 §9의 활성 → 폐기 전이 중 <b>여러 행을 한 번에</b> 끊는 경로를 모은다.
 * AuthService의 private 메서드였으나 로그아웃(AU-02)과 정지·비활성 훅(ON-09·MB-10)이
 * 같은 동작을 요구해 꺼냈다.
 *
 * <p><b>전파는 기본(REQUIRED)이다 — REQUIRES_NEW로 바꾸지 않는다.</b>
 * 호출자인 {@link AuthService#rotate}는 findByTokenHash에서 SELECT ... FOR UPDATE로
 * 그 행을 잠근 상태다. 별도 트랜잭션으로 분리하면 그 락을 쥔 채 같은 행을 UPDATE하려 들어
 * 무한 대기한다 — 락 대기 사이클이 아니라서 PostgreSQL 데드락 감지에도 걸리지 않는다.
 * 커밋 보장은 호출자가 맡는다 (rotate의 noRollbackFor).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SessionRevokeService {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 해당 구성원의 활성 세션을 전부 폐기한다.
     *
     * <p>벌크 UPDATE 대신 영속 엔티티로 로드해 revoke()를 부른다 — 상태 전이는 엔티티 메서드로 (14 §1.2).
     * 조회 조건이 ACTIVE라서 이미 폐기된 행은 애초에 걸리지 않는다.
     */
    public void revokeAllActive(UUID memberId, RefreshToken.RevokedReason reason, Instant now) {
        refreshTokenRepository
                .findByMemberIdAndStatus(memberId, RefreshToken.Status.ACTIVE)
                .forEach(token -> token.revoke(reason, now));
    }
}
