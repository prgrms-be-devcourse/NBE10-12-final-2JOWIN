package com.twojo.quote.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 견적 항목 — 작성 시점 카탈로그 단가·품목명·단위를 값 복사 (QT-24).
 * product 조인으로 표시하지 않는다 — PR-04 변경 시 고객이 본 견적서가 달라진다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id")
    private Quote quote;

    private UUID productId;   // null = 직접 입력 (QT-03)

    private String name;   // 값 복사

    private String unit;   // 값 복사

    private int quantity;

    private Long unitPrice;   // 0원 하한 (Q-02)

    private Long amount;

    private Long catalogPriceAtCreation;   // QT-24 · QT-29 확장 지점

    private int sortOrder;   // QT-07
}
