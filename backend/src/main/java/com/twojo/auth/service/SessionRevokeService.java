package com.twojo.auth.service;

import com.twojo.auth.SessionRevoker;
import com.twojo.auth.entity.RefreshToken;
import com.twojo.auth.repository.RefreshTokenRepository;
import com.twojo.boundary.MemberQuery;
import java.time.Instant;
import java.util.List;
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
public class SessionRevokeService implements SessionRevoker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberQuery memberQuery;

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

    /**
     * 구성원 비활성화 — 그 구성원의 세션만 전부 끊는다 (MB-09·10).
     * 담당 Deal 이관은 member 모듈의 몫이고, 여기는 "즉시 차단"의 실체만 맡는다.
     */
    @Override
    public void revokeOnDeactivation(UUID memberId, Instant now) {
        revokeAllActive(memberId, RefreshToken.RevokedReason.MEMBER_DEACTIVATED, now);
    }

    /**
     * 회사 정지 — 그 회사 전 구성원의 세션을 끊는다 (ON-08·09).
     *
     * <p>refresh_token에 company_id가 없어(06 ERD) 구성원 id를 먼저 모아야 한다.
     * 활성 구성원만 모으는 것으로 충분하다 — 비활성 구성원은 비활성화 시점에 이미 끊겼고,
     * 남아 있더라도 rotate()의 회사 상태 검사가 재발급을 막는다 (05 §9 안전망).
     */
    @Override
    public void revokeOnSuspension(UUID companyId, Instant now) {
        List<UUID> memberIds = memberQuery.findAllActive(companyId).stream()
                .map(MemberQuery.MemberSummary::id)
                .toList();

        // 빈 IN 절은 DB·JPA 버전에 따라 동작이 갈린다. 끊을 대상도 없으므로 여기서 접는다
        if (memberIds.isEmpty()) {
            return;
        }

        refreshTokenRepository
                .findByMemberIdInAndStatus(memberIds, RefreshToken.Status.ACTIVE)
                .forEach(token -> token.revoke(RefreshToken.RevokedReason.COMPANY_SUSPENDED, now));
    }
}
