package com.twojo.activity.repository;

import com.twojo.activity.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * 딜 타임라인의 자동 기록 (AC-06·07) — <b>병합 키는 {@code payload}의 {@code dealId}다</b>.
     *
     * <p>{@code entity_id}로는 못 찾는다. 견적 발송·고객 열람의 대상은 견적이고 주문 전환의
     * 대상은 주문이라, 딜에 걸린 사건을 모으려면 payload를 봐야 한다 (#22 규약, 06 audit_log).
     *
     * <p>JPQL에 jsonb 연산자가 없어 네이티브다. {@code ->>}는 텍스트를 돌려주므로 uuid를
     * 문자열로 맞춰 비교한다.
     */
    @Query(value = """
            select * from audit_log
            where company_id = :companyId
              and payload ->> 'dealId' = cast(:dealId as text)
            order by occurred_at desc
            """, nativeQuery = true)
    List<AuditLog> findByDealId(@Param("companyId") UUID companyId, @Param("dealId") UUID dealId);

    /**
     * 기간 안의 단계 전이 (DB-07 전환율의 원천, {@code AuditQuery.stageChanges}).
     *
     * <p>기간은 <b>사건이 일어난 시각</b> 기준이다 — 적재 시각이 아니다. 비동기 적재라 둘이
     * 밀릴 수 있고, 집계가 묻는 것은 "그 기간에 무슨 일이 있었나"다.
     *
     * <p><b>하한은 포함, 상한은 제외다.</b> 양 끝을 포함하면 인접한 두 기간의 경계에서 한 건이
     * 두 번 세어지고, 그것을 피하려고 상한을 당기면 그 사이 마이크로초에 일어난 전이가 어느
     * 기간에도 들어가지 않는다. 상한을 제외하면 다음 구간의 하한과 정확히 맞물린다.
     * 같은 파일의 목록 조회({@code search})도 같은 규칙이다 (#289).
     */
    List<AuditLog> findByCompanyIdAndEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            UUID companyId, String eventType, Instant from, Instant to);


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
