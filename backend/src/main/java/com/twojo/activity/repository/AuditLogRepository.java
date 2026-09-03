package com.twojo.activity.repository;

import com.twojo.activity.entity.AuditLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사 로그 조회 (AC-11) — 기업 관리자 전용, 회사 스코프 필수 (SC-01).
 *
 * <p>삭제 API가 없다 — 감사 기록은 불변이다 (11 §1.5).
 * 목록은 payload를 안 싣고 상세에서만 펼친다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** 상세 (GET /audit-logs/{id}) — 회사 스코프. 못 찾으면 404로 변환한다 (SC-09). */
    Optional<AuditLog> findByIdAndCompanyId(UUID id, UUID companyId);

    // 목록(GET /audit-logs?entityType=&from=&to=)은 여기 없다.
    // 세 파라미터가 전부 선택이라 메서드 이름으로 파생되는 쿼리로는 표현할 수 없다 —
    // Between은 null을 못 받아 기간 미지정 호출이 막히고, entityType 조건도 붙일 수 없다.
    // Specification이나 @Query가 필요한데, 소비처(감사 로그 조회 API)가 붙을 때
    // 실제 파라미터를 보고 짜는 편이 맞다. (docs/07 §B)
}
