package com.twojo.activity.repository;

import com.twojo.activity.entity.AuditLog;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 감사 로그 조회 (AC-11) — 기업 관리자 전용, 회사 스코프 필수 (SC-01).
 *
 * <p>삭제 API가 없다 — 감사 기록은 불변이다 (11 §1.5).
 * 목록은 payload를 안 싣고 상세에서만 펼친다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** 상세 (GET /audit-logs/{id}) — 회사 스코프. 못 찾으면 404로 변환한다 (SC-09). */
    Optional<AuditLog> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * 목록 (GET /audit-logs?entityType=&from=&to=) — 회사 스코프는 항상, 나머지 셋은 null이면 조건에서 빠진다.
     *
     * <p><b>파생 쿼리로는 못 짠다.</b> {@code Between}이 null을 못 받아 기간 미지정 호출이 막히고,
     * {@code entityType}을 선택적으로 붙일 방법도 없다. 조합이 여덟이라 메서드를 나눌 수도 없다.
     *
     * <p><b>{@code cast(:param as ...)}은 장식이 아니다.</b> 값이 null이면 JDBC가 타입을 몰라
     * 엉뚱한 타입으로 바인딩해 PostgreSQL이 거부한다 — {@code CustomerRepository.search}가
     * 같은 자리에서 {@code lower(bytea)} 오류로 500을 냈다.
     *
     * <p>기간은 {@code occurred_at} 기준이다 — 기록된 시각이 아니라 사건이 일어난 시각이다.
     */
    @Query("""
            select a from AuditLog a
            where a.companyId = :companyId
              and (cast(:entityType as string) is null or a.entityType = cast(:entityType as string))
              and (cast(:from as timestamp) is null or a.occurredAt >= cast(:from as timestamp))
              and (cast(:to as timestamp) is null or a.occurredAt <= cast(:to as timestamp))
            """)
    Page<AuditLog> search(@Param("companyId") UUID companyId,
                          @Param("entityType") String entityType,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
