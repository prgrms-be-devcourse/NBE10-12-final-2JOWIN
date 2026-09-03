package com.twojo.product.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 카탈로그 — UNIQUE(company_id, name)는 판매 중지 포함 (재등록 대신 판매 재개).
 * 단가·이름 변경은 기존 견적 무영향 — 견적이 값 복사하기 때문 (QT-24, PR-07·08).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    public enum Status { ACTIVE, DISCONTINUED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private String name;

    private String unit;

    private Long unitPrice;   // 원 단위 정수 (Q-12)

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * 상품 등록 (PR-01·02) — 새 상품은 항상 판매 중으로 시작한다.
     * {@code unitPrice}는 세전이다 (Q-46).
     */
    public static Product create(UUID companyId, String name, String unit,
                                 Long unitPrice, String description) {
        Product product = new Product();
        product.companyId = Objects.requireNonNull(companyId, "companyId");
        product.name = Objects.requireNonNull(name, "name");
        product.unit = Objects.requireNonNull(unit, "unit");
        product.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        product.description = description;
        product.status = Status.ACTIVE;
        return product;
    }

    /**
     * 상품 수정 (PR-04·08) — <b>null로 온 필드는 바꾸지 않는다</b> (08 §B의 PATCH 주석).
     * 단가 하나만 바꾸는 요청이 이름·단위까지 함께 보내도록 강요하지 않는다.
     * 설명은 빈 문자열로 비운다 — null은 "안 보냈다"는 뜻이다.
     *
     * <p>이름·단위가 공백만인 경우는 DTO의 {@code @Pattern}이 막는다.
     * 이름 중복 검사와 역할 검사는 서비스가 한다 — 엔티티는 값만 바꾼다.
     */
    public void update(String name, String unit, Long unitPrice, String description) {
        if (name != null) {
            this.name = name;
        }
        if (unit != null) {
            this.unit = unit;
        }
        if (unitPrice != null) {
            this.unitPrice = unitPrice;
        }
        if (description != null) {
            this.description = description;
        }
    }

    /**
     * 판매 중지 (PR-05) — 이미 중지된 상품이면 그대로 두므로 재호출이 안전하다.
     *
     * <p>던질 에러 코드가 07 부록·ErrorCode 어디에도 없어 예외 대신 무동작으로 둔다.
     * 차단이 필요해지면 서비스에서 사전 검사한다 (2JO-후속작업 1번).
     * 중지해도 <b>기존 견적은 그대로다</b> — 견적이 값을 복사해 두기 때문이다 (PR-07).
     */
    public void discontinue() {
        this.status = Status.DISCONTINUED;
    }

    /**
     * 판매 재개 — 중지한 이름으로 재등록하면 {@code UNIQUE(company_id, name)}에 걸리므로
     * (중지 상품도 포함) 이쪽이 정식 경로다.
     */
    public void reactivate() {
        this.status = Status.ACTIVE;
    }
}
