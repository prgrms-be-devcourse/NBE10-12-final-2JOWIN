package com.twojo.auth.repository;

import com.twojo.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** password_reset_token 접근 — auth 모듈 내부 (11 §7.3). */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * 재설정 실행 대상 — token_hash UNIQUE (06).
     *
     * <p>SELECT ... FOR UPDATE로 그 행을 잠근다. 락이 없으면 같은 링크로 온 두 요청이
     * 서로의 UPDATE 이전에 읽어 둘 다 ACTIVE로 판정하고, 한 토큰으로 비밀번호가 두 번
     * 바뀐다 — 05 §10이 전제하는 1회성이 깨진다. RefreshTokenRepository와 같은 이유다.
     *
     * <p>호출자는 트랜잭션 안이어야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * 재설정 재요청 시 만료시킬 기존 행 (05 §10 "기존 활성 행은 EXPIRED — 활성 1개 유지").
     *
     * <p>List가 아니라 Optional인 근거는 DB에 있다 — uk_password_reset_token_active가
     * status='ACTIVE'인 행을 구성원당 하나로 강제한다 (06).
     */
    Optional<PasswordResetToken> findByMemberIdAndStatus(UUID memberId, PasswordResetToken.Status status);
}
