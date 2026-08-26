package com.twojo.boundary;

import java.util.List;
import java.util.UUID;

/**
 * 구성원 조회 계약 — 구현: A(member 모듈). B·C·D는 member 테이블을 직접 조회하지 않는다.
 * (docs/11-work-breakdown.md §2)
 */
public interface MemberQuery {

    /** 이름 표시용 (B·C·D) */
    MemberSummary get(UUID memberId);

    /** 담당자 선택지 (C) */
    List<MemberSummary> findAllActive(UUID companyId);

    /** 배정·이관 대상 검증 (C) */
    boolean isActive(UUID memberId);

    /** Q-26 폴백 수신자 (D) */
    List<UUID> findAdminIds(UUID companyId);

    record MemberSummary(UUID id, String name, boolean active) {}
}
