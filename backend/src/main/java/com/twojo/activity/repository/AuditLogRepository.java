package com.twojo.activity.repository;

import com.twojo.activity.entity.AuditLog;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 감사 로그 조회 (AC-11) — 기업 관리자 전용, 회사 스코프 필수 (SC-01).
 *
 * <p>삭제 API가 없다 — 감사 기록은 불변이다 (11 §1.5).
 * 목록은 payload를 안 싣고 상세에서만 펼친다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    /** 상세 (GET /audit-logs/{id}) — 회사 스코프. 못 찾으면 404로 변환한다 (SC-09). */
    Optional<AuditLog> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * 목록 (GET /audit-logs?entityType=&from=&to=) — 조건 조립은 {@link AuditLogSpecs}가 한다.
     *
     * <p>파생 쿼리로는 못 짠다. {@code Between}이 null을 못 받아 기간 미지정 호출이 막히고
     * 조합이 여덟이라 메서드를 나눌 수도 없다. {@code @Query}도 아니다 — 사유는 {@code AuditLogSpecs}.
     */
    default Page<AuditLog> search(UUID companyId, String entityType,
                                  Instant from, Instant to, Pageable pageable) {
        return findAll(AuditLogSpecs.search(companyId, entityType, from, to), pageable);
    }
}
