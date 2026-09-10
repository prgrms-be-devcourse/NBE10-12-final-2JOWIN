package com.twojo.order.entity;

import com.twojo.boundary.QuoteCommand;
import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

    /**
     * 스냅샷 항목 (OD-04).
     *
     * <p><b>{@code @OrderBy}가 없다 — 걸 대상이 없어서다.</b> {@code quote_item}에는
     * {@code sort_order}가 있는데 {@code order_item}에는 없다 (V1 baseline). 그래서
     * 전환 직후 201 응답은 삽입 순서(=견적 순서)가 유지되지만, 이후
     * {@code GET /orders/{id}}는 DB가 돌려주는 순서라 <b>같은 주문인데 조회 시점에 따라
     * 항목 순서가 달라질 수 있다</b>.
     *
     * <p><b>정렬 축은 견적에서 물려받은 {@code sortOrder}다</b> (V301). 삽입 순서에 기대면
     * 전환 직후 응답은 맞지만 재조회에서 갈린다 — 1차 캐시가 비면 DB가 돌려주는 순서가
     * 그대로 나오기 때문이다. {@code quote_item}이 같은 문제를 같은 방식으로 푼다.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<OrderItem> items = new ArrayList<>();

    /**
     * 승인 견적 → 주문 (OD-01·04·07) — <b>스냅샷을 값으로 복사한다</b>.
     *
     * <p>금액도 항목도 견적에서 <b>받아서 가진다</b>. 조인해서 보여주는 것이 아니라
     * 자기 테이블에 적어 두기 때문에, 전환 뒤 견적을 고쳐도 주문은 변하지 않는다 (OD-05).
     * {@code order_item}에 견적 FK가 없는 것도 같은 결정이다 — <b>주문은 그 시점의 합의를
     * 고정하는 문서다.</b>
     *
     * <p><b>번호는 받아서 가진다 — 엔티티가 채번하지 않는다.</b> 채번은 카운터 행에 배타 락을
     * 걸어야 해서 트랜잭션과 리포지토리가 필요하다 ({@code Quote.draft}와 같은 규약, #72).
     *
     * <p><b>착수일·납기는 비어 있다</b> (OD-10) — 전환 시점에 정해지지 않은 값이고,
     * DDL도 {@code start_date}·{@code delivery_date}를 nullable로 둔다.
     */
    public static Order from(UUID companyId, QuoteCommand.ConversionSnapshot snapshot, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("주문 번호 없이 주문을 만들 수 없습니다.");
        }
        Order order = new Order();
        order.companyId = companyId;
        order.quoteId = snapshot.quoteId();
        order.orderNo = orderNo;
        order.supplyAmount = snapshot.supplyAmount();
        order.vatAmount = snapshot.vatAmount();
        order.totalAmount = snapshot.totalAmount();
        snapshot.items().forEach(line -> {
            OrderItem item = OrderItem.of(line.name(), line.unit(),
                    line.quantity(), line.unitPrice(), line.amount(), line.sortOrder());
            item.assignTo(order);
            order.items.add(item);
        });
        return order;
    }

    /**
     * 착수일·납기 기록 (OD-10) — <b>상태 전이가 아니다</b> (전이표 §8, Q-09).
     *
     * <p>주문에는 상태가 없어서 "착수함"·"납품함" 같은 단계가 없다. 두 날짜는 그저 기록이고,
     * 언제든 다시 적을 수 있다. 조건 없이 덮어쓰는 이유가 여기 있다.
     *
     * <p><b>둘을 함께 덮어쓴다 — null은 "미변경"이 아니라 "지움"이다.</b> 착수일과 납기는
     * 하나의 일정이라 따로 쓰면 "납기가 착수일보다 앞선" 조합이 부분 수정으로 만들어진다.
     * 게다가 주문에는 다른 수정 경로가 없어서(취소도 없다, OD-11·12 제외) 여기서 지우지
     * 못하면 잘못 적은 날짜를 되돌릴 방법이 아예 없다.
     */
    public void updateSchedule(LocalDate startDate, LocalDate deliveryDate) {
        this.startDate = startDate;
        this.deliveryDate = deliveryDate;
    }
}
