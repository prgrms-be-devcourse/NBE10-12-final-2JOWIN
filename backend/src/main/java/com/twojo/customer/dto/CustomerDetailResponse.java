package com.twojo.customer.dto;

import com.twojo.boundary.DealQuery;
import com.twojo.customer.entity.Customer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 고객사 상세 응답 (CU-05·12) — 담당자 목록과 Deal 이력을 함께 싣는다.
 *
 * <p>{@code deals}는 C의 {@code DealQuery.summariesByCustomer()}로 받아 서비스가 옮긴다.
 * {@code createdByMemberName}은 A의 {@code MemberQuery.get()}으로 채운다.
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

    /**
     * 엔티티와 경계 조회 결과 → 응답.
     *
     * <p>{@code createdByMemberName}은 호출부가 채워 넘긴다 — 사유는 그쪽 주석에 있다.
     */
    public static CustomerDetailResponse of(Customer customer, String createdByMemberName,
                                            List<ContactResponse> contacts,
                                            List<DealQuery.DealSummary> deals) {
        return new CustomerDetailResponse(customer.getId(), customer.getName(), customer.getIndustry(),
                customer.getSize(), customer.getNote(), customer.getCreatedByMemberId(),
                createdByMemberName, contacts,
                deals.stream().map(DealSummary::of).toList(),
                customer.getCreatedAt());
    }

    /** 고객사 상세의 Deal 이력 한 줄 (CU-12). */
    public record DealSummary(
            UUID id,
            String title,
            String stage,
            Long expectedAmount,
            Long wonAmount,
            Instant createdAt) {

        /**
         * C의 경계 record → 응답 record. 필드가 같아도 그대로 노출하지 않는다 —
         * {@code boundary}가 바뀔 때 API 응답이 따라 움직이면 안 된다 (11 §7.2).
         */
        static DealSummary of(DealQuery.DealSummary summary) {
            return new DealSummary(summary.id(), summary.title(), summary.stage(),
                    summary.expectedAmount(), summary.wonAmount(), summary.createdAt());
        }
    }
}
