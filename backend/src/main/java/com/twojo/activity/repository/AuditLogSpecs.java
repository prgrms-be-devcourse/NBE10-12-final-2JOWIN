package com.twojo.activity.repository;

import com.twojo.activity.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * 감사 로그 목록 조건 조립 (AC-11).
 *
 * <p>회사 스코프는 <b>항상</b> 걸린다 — 선택 필터가 아니라 기반 조건이다 (SC-01).
 *
 * <p><b>{@code @Query}로 짜지 않는 이유</b>: 세 필터가 전부 선택이라 null이면 조건을 빼야 하는데,
 * JPQL에서 {@code cast(:from as timestamp)}로 푸는 방식은 PostgreSQL이 거부한다 —
 * 타입 없는 null이 {@code bytea}로 바인딩되고 {@code bytea → timestamp} 캐스트가 없다
 * ({@code cannot cast type bytea to timestamp}). 문자열은 {@code bytea → varchar}가 되어
 * {@code CustomerRepository.search}에서는 같은 방식이 통했지만 시각에는 통하지 않는다.
 * 조건을 아예 안 붙이면 바인딩할 파라미터가 없어 이 문제가 사라진다.
 */
final class AuditLogSpecs {

    private AuditLogSpecs() {
    }

    /**
     * @param entityType null이면 전 엔티티
     * @param from       null이면 시작 제한 없음. 기준은 사건이 일어난 시각이다 (기록 시각이 아니다). 포함
     * @param to         null이면 끝 제한 없음. <b>제외</b> — 반개구간, {@code stageChanges} 파생 쿼리와 같다 (#289)
     */
    static Specification<AuditLog> search(UUID companyId, String entityType,
                                          Instant from, Instant to) {
        Specification<AuditLog> spec = inCompany(companyId);
        if (entityType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("occurredAt"), to));
        }
        return spec;
    }

    /** 기반 조건 — 회사 스코프. 어떤 목록 조회도 이걸 건너뛰지 않는다 (SC-01) */
    private static Specification<AuditLog> inCompany(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }
}
