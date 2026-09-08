package com.twojo.order.entity;

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

/** 주문 항목 — FK 없는 값 복사 스냅샷 (OD-04). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    private String name;

    private String unit;

    private int quantity;

    private Long unitPrice;

    private Long amount;

    /**
     * 전환 시점 값 복사 (OD-04).
     *
     * <p><b>{@code productId}도 {@code quoteItemId}도 없다.</b> DDL이 {@code order_id}만
     * 참조하도록 정해 두었고(ERD "주문 항목은 FK 없이 값 복사"), 견적 항목을 조인하면
     * 전환 뒤 견적 수정이 주문 내용을 따라 바꿔 OD-05가 깨진다.
     *
     * <p>{@code amount}도 받아서 가진다 — 여기서 {@code quantity × unitPrice}로 다시 계산하면
     * 견적이 확정한 값과 주문이 적은 값이 갈릴 여지가 생긴다. <b>스냅샷은 옮겨 적는 것이지
     * 다시 세는 것이 아니다.</b>
     */
    public static OrderItem of(String name, String unit, int quantity, Long unitPrice, Long amount) {
        OrderItem item = new OrderItem();
        item.name = name;
        item.unit = unit;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.amount = amount;
        return item;
    }

    /** 양방향 연관의 주인 쪽을 채운다 — {@code order_id}가 NOT NULL이라 저장 전에 반드시 선다 */
    void assignTo(Order order) {
        this.order = order;
    }
}
