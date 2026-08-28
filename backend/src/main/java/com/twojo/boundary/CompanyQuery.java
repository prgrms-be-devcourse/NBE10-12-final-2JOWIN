package com.twojo.boundary;

import java.util.UUID;

/**
 * 회사 조회 계약 — 구현: A(onboarding 모듈). 다른 모듈은 company 테이블을 직접 조회하지 않는다.
 *
 * <p>11 §7.2 인터페이스 목록에 빠져 있던 것을 메운다. 회사명이 필요한 곳이 둘이다 —
 * 로그인 응답(08 §A LoginResponse.companyName)과 고객 열람 응답(07 §D PublicQuoteResponse.companyName).
 */
public interface CompanyQuery {

    /** 없으면 RESOURCE_NOT_FOUND — FK가 존재를 보장하는 자리라 없다는 것은 데이터 이상이다. */
    CompanySummary get(UUID companyId);

    /**
     * active는 정지 여부다 (ON-08~10). 이번 사이클에서는 쓰지 않지만,
     * SC-10(정지 회사의 승인·반려 차단)이 이 값을 필요로 한다 — 필드 매핑이라 로직 위험이 없다.
     */
    record CompanySummary(UUID id, String name, boolean active) {}
}
