package com.twojo.approval.repository;

import com.twojo.approval.entity.QuoteViewToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteViewTokenRepository extends JpaRepository<QuoteViewToken, UUID> {

    /** 열람 요청 — raw 토큰을 해시해 행을 찾는다 (SC-07~09, 없으면 404). */
    Optional<QuoteViewToken> findByTokenHash(String tokenHash);

    /** 견적의 특정 상태 링크 조회 — 재발송·만료 대상인 ACTIVE 1건 (AP-03 부분 유니크). */
    Optional<QuoteViewToken> findByQuoteIdAndStatus(UUID quoteId, QuoteViewToken.Status status);

    /** 발송(수신인 지정) 이력 존재 여부 — B의 CU-14 담당자 삭제 차단 판정 (ViewTokenQuery.existsForContact). */
    boolean existsByRecipientContactId(UUID recipientContactId);
}
