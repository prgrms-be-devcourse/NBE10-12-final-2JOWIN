package com.twojo.boundary;

/** 조회 범위 (docs/11-work-breakdown.md §1.4). */
public enum AccessScope {
    /** 기업 관리자 — 회사 전체 (SC-05) */
    COMPANY_ALL,
    /** 영업 담당자 — deal.assignee_member_id 기준 본인 담당만 (SC-02·04) */
    OWNED_ONLY
}
