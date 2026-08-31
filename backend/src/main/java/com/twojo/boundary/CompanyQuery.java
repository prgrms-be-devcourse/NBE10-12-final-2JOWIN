package com.twojo.boundary;

import java.util.UUID;

/**
 * 회사 조회 계약 — 구현: A(onboarding 모듈). 다른 모듈은 company 테이블을 직접 조회하지 않는다.
 *
 * <p>표시용 조회와 정지 판정을 나눈다. 소비자가 다르기 때문이다 — 회사명이 필요한 곳은
 * 로그인 응답(08 §A LoginResponse.companyName) 하나뿐이고, 정지 여부는 인증 경로 셋이
 * 매번 묻는다 (로그인 · refresh 회전 · 인증 필터. 07 §A · ON-09).
 */
public interface CompanyQuery {

    /** 없으면 RESOURCE_NOT_FOUND — FK가 존재를 보장하는 자리라 없다는 것은 데이터 이상이다. */
    CompanyIdentity getIdentity(UUID companyId);

    /**
     * 정지 여부 (ON-08~10). 인증 필터가 요청마다 부르므로 표시 정보를 함께 싣지 않는다.
     *
     * <p>없는 회사는 true다. 이 값을 묻는 자리는 전부 인증 경로라, 판정할 수 없으면
     * 여는 쪽이 아니라 막는 쪽으로 접는다.
     */
    boolean isSuspended(UUID companyId);

    /**
     * 표시용. record라 필드를 나중에 더해도 소비자는 깨지지 않는다
     * (businessNo는 08 §D 문서 PR이 먼저다).
     */
    record CompanyIdentity(UUID id, String name) {}
}
