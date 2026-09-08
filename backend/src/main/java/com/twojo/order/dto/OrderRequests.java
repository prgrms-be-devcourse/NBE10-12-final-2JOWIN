package com.twojo.order.dto;

import java.time.LocalDate;

/** 주문 요청 DTO. 필드는 08의 {@code OrderScheduleRequest}를 따른다. */
public final class OrderRequests {

    private OrderRequests() {
    }

    /**
     * 착수일·납기 기록 (OD-10) — 08의 {@code OrderScheduleRequest}.
     *
     * <p><b>둘 다 null 허용이고, null은 "미변경"이 아니라 "지움"이다.</b> 두 날짜는 하나의
     * 일정이라 함께 덮어쓴다 — 이유는 {@code Order.updateSchedule} javadoc에 있다.
     *
     * <p>{@code version}이 없다. 주문에는 {@code @Version} 컬럼이 없고(ERD), 낙관적 락은
     * Deal·견적에만 걸린다 — 주문은 상태가 없어(Q-09) 경합으로 뒤집힐 전이가 없다.
     */
    public record UpdateSchedule(LocalDate startDate, LocalDate deliveryDate) {
    }
}
