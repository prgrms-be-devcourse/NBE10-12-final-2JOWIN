package com.twojo.boundary;

import java.util.UUID;

/**
 * 회사 조회 계약 — 구현: A(onboarding 모듈). 다른 모듈은 company 테이블을 직접 조회하지 않는다.
 *
 * <p>11 §7.2 인터페이스 목록에 빠져 있던 것을 메운다. 회사명이 필요한 곳이 둘이다 —
 * 로그인 응답(08 §A LoginResponse.companyName)과 고객 열람 응답(07 §D PublicQuoteResponse.companyName).
 */
public interface CompanyQuery {

    /**
     * 없으면 RESOURCE_NOT_FOUND — FK가 존재를 보장하는 자리라 없다는 것은 데이터 이상이다.
     *
     * <p><b>메서드를 늘리지 않는다.</b> 정지 여부만 필요한 호출자도 이것을 쓴다 —
     * {@code isSuspended} 같은 메서드를 따로 두면 <b>같은 행을 읽는 경로가 둘</b>이 되고,
     * 회사 상태가 {@code ACTIVE}/{@code SUSPENDED} 둘뿐이라 {@link CompanySummary#active()}
     * 하나로 판정이 끝난다. 고객 열람 페이지는 어차피 이름·사업자번호를 받으려고 이 메서드를
     * 부르므로, 그 한 번의 호출에서 정지 여부까지 함께 온다.
     */
    CompanySummary get(UUID companyId);

    /**
     * 한 번의 조회로 회사 표시와 정지 판정을 모두 덮는다.
     *
     * @param name       로그인 응답(08 §A) · 고객 열람 페이지 상단
     * @param businessNo 사업자등록번호 — 고객 열람 페이지가 회사명과 함께 최상단에 표시한다
     *                   (10-screen-design.md §5.6 · GAP-05 완화안). 전역 UNIQUE (06 §제약)
     * @param active     정지 여부 (ON-08~10). SC-10(정지 회사의 승인·반려 차단)과
     *                   Q-27(정지 중 배치 알림 억제)이 {@code !active()}로 판정한다
     */
    record CompanySummary(UUID id, String name, String businessNo, boolean active) {}
}
