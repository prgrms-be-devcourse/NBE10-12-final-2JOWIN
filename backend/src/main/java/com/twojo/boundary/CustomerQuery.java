package com.twojo.boundary;

import java.util.Collection;
import java.util.List;
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

    /**
     * 고객사 id 묶음 → 이름 배치 조회 — 목록 한 줄마다 고객사명이 붙는 자리의 유일한 창구다
     * (DB-03 응답 대기 카드 · 주문 목록).
     *
     * <p><b>줄마다 {@link #get}을 부르지 않게 배치로 받는다</b> — 목록 20건이면 조회도 20번이 된다.
     * 반환은 요청 순서를 보장하지 않으므로 호출자가 id로 인덱싱한다.
     * 빈 목록을 넘기면 빈 목록을 돌려준다.
     *
     * <p><b>없는 id는 결과에서 빠진다(예외 아님)</b> — {@link #get}이 같은 상황에서
     * {@code RESOURCE_NOT_FOUND}를 던지는 것과 다르다. 거기서는 "이 고객사가 맞는가"를 묻고
     * 답이 없으면 호출자가 진행할 수 없지만, 여기서는 <b>목록 한 줄의 고객사가 지워졌다고
     * 목록 전체가 실패하면 안 된다.</b> 소프트 삭제된 고객사도 같은 이유로 빠진다.
     *
     * <p>{@code AccessContext}가 아니라 {@code companyId}를 받는다 — 목록 조립 지점에는 그 값이
     * 없다. 고객사는 담당 개념이 없어 회사 스코프(SC-01)만으로 판정이 끝난다.
     */
    List<CustomerSummary> namesByIds(UUID companyId, Collection<UUID> customerIds);

    record CustomerSummary(UUID id, String name) {}

    record ContactSummary(UUID id, String name, String title, String email) {}
}
