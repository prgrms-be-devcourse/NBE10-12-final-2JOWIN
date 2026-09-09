package com.twojo.onboarding.repository;

import com.twojo.onboarding.entity.Company;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** company 테이블 접근 — 모듈 내부. 다른 모듈은 CompanyQuery로만 조회한다 (11 §7.3). */
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    /**
     * 사업자번호 전역 중복 (COMPANY_BUSINESS_NO_DUPLICATED) — 승인 전에 본다.
     *
     * <p>유니크 제약이 이미 같은 것을 막지만, 그쪽에 맡기면 409 대신 제약 위반 500이 나간다.
     * 07 §A는 "이미 가입된 회사입니다"로 반려를 유도하라고 규정한다.
     *
     * <p>재신청을 허용하므로(Q-15) 신청 쪽에는 이 검사를 두지 않는다 — 막을 자리는 승인이다.
     */
    boolean existsByBusinessNo(String businessNo);

    /** 승인 멱등 — application_id UNIQUE. 이미 회사가 있으면 두 번째 승인은 막힌다. */
    boolean existsByApplicationId(UUID applicationId);

    /**
     * 상태로 거른 회사 id — 배치의 회사 순회(NT-05·06)가 쓴다.
     *
     * <p>id만 뽑는다. 엔티티를 실어 오면 배치가 쓰지도 않을 이름·사업자번호·정지 사유까지
     * 회사 수만큼 영속성 컨텍스트에 쌓인다.
     */
    @Query("select c.id from Company c where c.status = :status")
    List<UUID> findIdsByStatus(Company.Status status);
}
