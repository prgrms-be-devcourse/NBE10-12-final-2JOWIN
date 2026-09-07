package com.twojo.customer.dto;

import com.twojo.customer.entity.Customer;
import java.time.Instant;
import java.util.UUID;

/**
 * 고객사 목록·단건 응답 (CU-03·04).
 *
 * <p>{@code createdByMemberId}는 <b>기록용이며 권한 판정 축이 아니다</b> — 고객사는 회사 공유
 * 자원이라 담당 개념이 없다 (SC-03, v1.2에서 owner → created_by 확정).
 */
public record CustomerResponse(
        UUID id,
        String name,
        String industry,
        String size,
        String note,
        UUID createdByMemberId,
        Instant createdAt) {

    /** 엔티티 → 응답. 응답 모양이 바뀔 때 열 파일을 하나로 둔다 — 서비스는 판단만 담는다. */
    public static CustomerResponse of(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getIndustry(),
                customer.getSize(), customer.getNote(), customer.getCreatedByMemberId(),
                customer.getCreatedAt());
    }
}
