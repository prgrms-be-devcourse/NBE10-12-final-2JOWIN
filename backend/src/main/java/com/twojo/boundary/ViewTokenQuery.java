package com.twojo.boundary;

import java.util.UUID;

/**
 * 열람 링크 조회 계약 — 구현: D(approval 모듈). B가 호출한다 (v2.0.1 보강).
 * (docs/11-work-breakdown.md §5)
 */
public interface ViewTokenQuery {

    /** 발송 이력(수신인 지정 이력) 존재 — true면 CU-14 CONTACT_HAS_QUOTES로 삭제 차단 */
    boolean existsForContact(UUID contactId);
}
