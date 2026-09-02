package com.twojo.activity.repository;

import com.twojo.activity.entity.AuditLog;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사 로그 조회 (AC-11) — 기업 관리자 전용, 회사 스코프 필수 (SC-01).
 *
 * <p>삭제 API가 없다 — 감사 기록은 불변이다 (11 §1.5).
 * 목록은 payload를 안 싣고 상세에서만 펼친다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** 목록 (GET /audit-logs) — 기간 필터 + 발생 시각 내림차순. */
    Page<AuditLog> findByCompanyIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID companyId, Instant from, Instant to, Pageable pageable);

    /** 상세 (GET /audit-logs/{id}) — 회사 스코프. 못 찾으면 404로 변환한다 (SC-09). */
    Optional<AuditLog> findByIdAndCompanyId(UUID id, UUID companyId);
}
