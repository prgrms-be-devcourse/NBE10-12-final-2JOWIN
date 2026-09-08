package com.twojo.activity.repository;

import com.twojo.activity.entity.Activity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    /**
     * 대시보드 최근 활동 — <b>회사 범위</b> (기업 관리자, COMPANY_ALL · SC-05).
     *
     * <p>기준 시각은 기록 시각이 아니라 <b>활동 발생 시각</b>이다 (AC-01). 동률은 {@code id}로
     * 안정화한다 — 순서가 흔들리면 새로고침마다 목록이 바뀐다.
     *
     * <p>건수는 {@code Pageable}로 받는다. 상한 판정은 호출부의 몫이다 ({@code ActivityQuery.MAX_LIMIT}).
     */
    List<Activity> findByCompanyIdAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
            UUID companyId, Pageable pageable);

    /**
     * 대시보드 최근 활동 — <b>담당 딜 범위</b> (영업, OWNED_ONLY · SC-02·04).
     *
     * <p>{@code dealIds}는 {@code DealQuery.assignedDealIds(companyId, memberId)}가 준 목록이라
     * 이미 회사로 걸러져 있지만, 회사 조건을 함께 건다 — 13 §2 셀프 체크리스트가
     * <b>"모든 조회에 회사 스코프"</b>로 정했다.
     *
     * <p><b>빈 목록을 넘기지 말 것.</b> 빈 {@code IN} 절은 처리 방식이 환경에 따라 다르다.
     * 담당 딜이 없으면 호출부가 조회하지 않고 빈 결과를 돌려준다.
     */
    List<Activity> findByCompanyIdAndDealIdInAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
            UUID companyId, Collection<UUID> dealIds, Pageable pageable);
}
