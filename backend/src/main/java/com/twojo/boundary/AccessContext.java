package com.twojo.boundary;

import java.util.UUID;

/**
 * 접근 컨텍스트 — 모든 조회가 인자로 받는다 (docs/11-work-breakdown.md §1.4).
 * <p>담당 판정 축은 deal.assignee_member_id 하나뿐이다. 견적·주문·상담·할 일의 범위는 전부 Deal에서 파생된다.
 */
public record AccessContext(UUID companyId, UUID memberId, Role role, AccessScope scope) {}
