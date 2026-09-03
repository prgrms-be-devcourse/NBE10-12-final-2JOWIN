package com.twojo.activity.repository;

import com.twojo.activity.entity.Activity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상담 기록 조회 (AC) — 모든 조회에 회사 스코프와 소프트 삭제 조건이 함께 걸린다 (SC-01, 11 §1.5).
 *
 * <p><b>조회에 {@code author_member_id} 조건을 걸지 않는다.</b> 담당이 이관돼도 이전 담당자가
 * 남긴 기록은 계속 보여야 하므로(AC-08 · PB-05) 작성자는 조회 축이 아니다. 작성자는
 * 수정·삭제 판정에만 쓴다 (AC-04·05).
 */
public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    /** 딜 타임라인 (AC-06) — 발생 시각 내림차순. 자동 기록은 audit_log에서 따로 와 병합된다. */
    List<Activity> findByCompanyIdAndDealIdAndDeletedAtIsNullOrderByOccurredAtDesc(
            UUID companyId, UUID dealId);

    /** 수정·삭제 대상 조회 — 회사 스코프 + 미삭제. 못 찾으면 호출부에서 404로 변환한다 (SC-09). */
    Optional<Activity> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);
}
