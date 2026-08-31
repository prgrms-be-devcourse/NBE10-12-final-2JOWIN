package com.twojo.auth.repository;

import com.twojo.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** refresh_token 접근 — auth 모듈 내부 (11 §7.3). */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * 회전 대상 조회 — token_hash UNIQUE.
     *
     * <p>SELECT ... FOR UPDATE로 그 행을 잠근다. 락이 없으면 같은 토큰으로 온 두 요청이
     * 서로의 UPDATE 이전에 읽어 둘 다 ACTIVE로 판정하고, 한 토큰에서 유효한 세션이
     * 둘 생긴다 — 05 §9가 전제하는 1:1 회전이 깨진다.
     *
     * <p>호출자는 트랜잭션 안이어야 한다 (AuthService#rotate).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 재사용 감지 시 폐기 대상 — 해당 구성원의 활성 세션 전부 (전이표 §9).
     * 벌크 UPDATE 대신 로드해서 revoke()를 부른다 — 상태 전이는 엔티티 메서드로 (14 §1.2).
     */
    List<RefreshToken> findByMemberIdAndStatus(UUID memberId, RefreshToken.Status status);
}
