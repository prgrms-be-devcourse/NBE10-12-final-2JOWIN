package com.twojo.approval.repository;

import com.twojo.approval.entity.QuoteViewToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteViewTokenRepository extends JpaRepository<QuoteViewToken, UUID> {

    /** 열람 요청 — raw 토큰을 해시해 행을 찾는다 (SC-07~09, 없으면 404). */
    Optional<QuoteViewToken> findByTokenHash(String tokenHash);

    /**
     * 견적의 활성 링크 1건 — 재발송·만료 대상 (AP-03 부분 유니크: quote_id WHERE status='ACTIVE').
     * <p>status를 ACTIVE로 고정한다. EXPIRED는 재발송마다 누적돼 다건이 될 수 있어,
     * status를 파라미터로 열면 다건 조회 시 IncorrectResultSizeDataAccessException 위험이 생긴다.
     */
    @Query("select t from QuoteViewToken t where t.quoteId = :quoteId and t.status = ACTIVE")
    Optional<QuoteViewToken> findActiveByQuoteId(@Param("quoteId") UUID quoteId);

    /** 발송(수신인 지정) 이력 존재 여부 — B의 CU-14 담당자 삭제 차단 판정 (ViewTokenQuery.existsForContact). */
    boolean existsByRecipientContactId(UUID recipientContactId);
}
