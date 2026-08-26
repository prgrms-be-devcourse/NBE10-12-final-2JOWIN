package com.twojo.order.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 — 상태 없음 (Q-09·전이표 §8). 1견적 1주문 (OD-03, UNIQUE(quote_id)).
 * 금액·항목은 전환 시점 스냅샷 (OD-04) — 이후 견적 변경 무영향 (OD-05).
 * deal_id 컬럼 없음 — quote 경유 조회.
 */
@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private UUID quoteId;

    private String orderNo;   // OD-07 — document_sequence 채번

    private Long supplyAmount;

    private Long vatAmount;

    private Long totalAmount;

    private LocalDate startDate;   // OD-10 — 상태가 아니라 날짜 필드

    private LocalDate deliveryDate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
}
