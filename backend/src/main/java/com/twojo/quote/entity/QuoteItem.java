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

    /**
     * 항목 생성 — amount는 단가 x 수량으로 <b>서버가 계산한다</b> (QT-08, 검증 노트 #1).
     *
     * @param productId                null이면 직접 입력 (QT-03)
     * @param catalogPriceAtCreation   작성 시점 카탈로그 단가 (QT-24). 직접 입력이면 null
     */
    public static QuoteItem of(UUID productId, String name, String unit,
                               int quantity, Long unitPrice,
                               Long catalogPriceAtCreation, int sortOrder) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다: " + quantity);
        }
        if (unitPrice == null || unitPrice < 0) {
            throw new IllegalArgumentException("단가는 0원 이상이어야 합니다: " + unitPrice);   // 0원 하한 (Q-02)
        }
        QuoteItem item = new QuoteItem();
        item.productId = productId;
        item.name = name;
        item.unit = unit;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.amount = Math.multiplyExact(unitPrice, (long) quantity);
        item.catalogPriceAtCreation = catalogPriceAtCreation;
        item.sortOrder = sortOrder;
        return item;
    }

    /** 양방향 연관 설정 — {@link Quote#replaceItems} 에서만 호출한다 */
    void assignTo(Quote quote) {
        this.quote = quote;
    }
}
