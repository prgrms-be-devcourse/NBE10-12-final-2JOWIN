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
     * 고객사 수정 (CU-06) — <b>온 값을 그대로 반영한다.</b>
     *
     * <p>08 §B의 {@code UpdateCustomerRequest}에는 "PATCH: null 필드는 미변경" 주석이 없고
     * {@code name}이 {@code @NotBlank}라, 수정 폼이 기존 값을 채워 전체를 보내는 것을 전제한다.
     * 그래야 비고 같은 선택 항목을 비워서 지울 수 있다.
     * ({@link CustomerContact#update}는 반대 — 보낸 필드만 바꾼다)
     */
    public void update(String name, String industry, String size, String note) {
        this.name = Objects.requireNonNull(name, "name");
        this.industry = industry;
        this.size = size;
        this.note = note;
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
