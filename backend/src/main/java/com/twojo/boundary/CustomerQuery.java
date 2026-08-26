package com.twojo.boundary;

import java.util.UUID;

/**
 * 고객사 조회 계약 — 구현: B(customer 모듈). (docs/11-work-breakdown.md §3)
 */
public interface CustomerQuery {

    /** C의 Deal 생성 검증·표시 */
    CustomerSummary get(AccessContext ctx, UUID customerId);

    /** C의 CONTACT_NOT_IN_CUSTOMER 검증 — 복합 FK 불가 영역, 서비스 검증이 유일 방어 */
    boolean existsContactInCustomer(UUID customerId, UUID contactId);

    /** D의 발송 수신자 정보 */
    ContactSummary getContact(UUID contactId);

    record CustomerSummary(UUID id, String name) {}

    record ContactSummary(UUID id, String name, String title, String email) {}
}
