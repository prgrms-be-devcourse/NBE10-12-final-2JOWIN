package com.twojo.onboarding.dto;

import com.twojo.onboarding.entity.Company;
import java.time.Instant;
import java.util.UUID;

/**
 * 회사 응답 (08 §A · ON-12).
 *
 * <p>{@code memberCount}가 "이용 현황"의 전부다 (Q-41). 딜·견적 건수는 플랫폼 관리자가
 * 영업 데이터를 조회할 수 없다는 ON-11의 경계에 닿는다 — 늘리려면 요구사항부터 늘린다.
 *
 * <p>구성원 수는 엔티티에 없다. 경계 밖(member 모듈)에서 오므로 팩토리가 인자로 받는다.
 */
public record CompanyResponse(
        UUID id,
        String name,
        String businessNo,
        String status,
        String suspendReason,
        int memberCount,
        Instant createdAt) {

    public static CompanyResponse of(Company company, int memberCount) {
        return new CompanyResponse(
                company.getId(), company.getName(), company.getBusinessNo(),
                company.getStatus().name(), company.getSuspendReason(),
                memberCount, company.getCreatedAt());
    }
}
