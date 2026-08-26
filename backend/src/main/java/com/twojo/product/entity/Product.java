package com.twojo.product.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
}
