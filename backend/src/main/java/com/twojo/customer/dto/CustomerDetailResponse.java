package com.twojo.customer.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 고객사 상세 응답 (CU-05·12) — 담당자 목록과 Deal 이력을 함께 싣는다.
 *
 * <p>{@code deals}는 C의 {@code DealQuery.summariesByCustomer()}로 받아 서비스가 옮긴다.
 * {@code createdByMemberName}은 A의 {@code MemberQuery.get()}으로 채운다 — 표시용이라
 * 없으면 {@code null}로 두고 진행한다.
 */
public record CustomerDetailResponse(
        UUID id,
        String name,
        String industry,
        String size,
        String note,
        UUID createdByMemberId,
        String createdByMemberName,
        List<ContactResponse> contacts,
        List<DealSummary> deals,
        Instant createdAt) {

    /** 고객사 상세의 Deal 이력 한 줄 (CU-12). */
    public record DealSummary(
            UUID id,
            String title,
            String stage,
            Long expectedAmount,
            Long wonAmount,
            Instant createdAt) {}
}
