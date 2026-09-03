package com.twojo.customer.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객사 — 회사 공유 자원, 담당 개념 없음 (SC-03).
 * created_by는 생성자 기록용 — 접근 판정에 쓰지 않는다. 소프트 삭제.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private UUID createdByMemberId;

    private String name;

    private String industry;

    private String size;

    @Column(columnDefinition = "text")
    private String note;

    private Instant deletedAt;

    /**
     * 고객사 등록 (CU-01·02). {@code companyId}·{@code createdByMemberId}는 AccessContext에서 온다.
     * {@code createdByMemberId}는 기록용이며 접근 판정에 쓰지 않는다 (SC-03).
     */
    public static Customer create(UUID companyId, UUID createdByMemberId, String name,
                                  String industry, String size, String note) {
        Customer customer = new Customer();
        customer.companyId = Objects.requireNonNull(companyId, "companyId");
        customer.createdByMemberId = Objects.requireNonNull(createdByMemberId, "createdByMemberId");
        customer.name = Objects.requireNonNull(name, "name");
        customer.industry = industry;
        customer.size = size;
        customer.note = note;
        return customer;
    }

    /**
     * 고객사 수정 (CU-06) — <b>null로 온 필드는 바꾸지 않는다</b> (08 §B의 PATCH 주석).
     *
     * <p>선택 항목을 비우는 경로는 빈 문자열이다. null은 "안 보냈다"는 뜻이라
     * 지우기와 구별된다. 이름이 공백만인 경우는 DTO의 {@code @Pattern}이 막는다.
     */
    public void update(String name, String industry, String size, String note) {
        if (name != null) {
            this.name = name;
        }
        if (industry != null) {
            this.industry = industry;
        }
        if (size != null) {
            this.size = size;
        }
        if (note != null) {
            this.note = note;
        }
    }

    /**
     * 소프트 삭제 (CU-07) — 이미 삭제됐으면 무동작이라 최초 시각이 유지된다.
     * 시각을 파라미터로 받는다: 삭제 시각이 감사·복구 기준이라 호출자가 정한다.
     */
    public void softDelete(Instant now) {
        if (deletedAt == null) {
            this.deletedAt = Objects.requireNonNull(now, "now");
        }
    }
}
