package com.twojo.auth.repository;

import com.twojo.auth.entity.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** refresh_token 접근 — auth 모듈 내부 (11 §7.3). */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** 회전 대상 조회 — token_hash UNIQUE. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 재사용 감지 시 폐기 대상 — 해당 구성원의 활성 세션 전부 (전이표 §9).
     * 벌크 UPDATE 대신 로드해서 revoke()를 부른다 — 상태 전이는 엔티티 메서드로 (14 §1.2).
     */
    List<RefreshToken> findByMemberIdAndStatus(UUID memberId, RefreshToken.Status status);
}
