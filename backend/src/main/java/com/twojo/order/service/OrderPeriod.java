package com.twojo.order.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 전환일 → 시각 경계 (OD-08 · DB-02·06).
 *
 * <p><b>이 규칙의 유일한 자리다.</b> {@code orders.created_at}이 전환 시각이고 그 컬럼은 이 모듈이
 * 소유하므로, 날짜를 시각으로 끊는 규칙도 여기 한 곳에만 둔다. 목록과 집계가 각자 계산하면
 * 한쪽만 고쳐질 때 "목록엔 있는데 이달 성사엔 없는" 주문이 생긴다 (#216 리뷰).
 *
 * <p><b>한국 날짜로 끊는다</b> — 사람이 읽는 날짜이고, 채번의 연월 판정과 같은 축이다 (#72).
 * 서버 시간대로 끊으면 자정 부근 전환이 옆 날짜·옆 달로 샌다.
 */
final class OrderPeriod {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private OrderPeriod() {
    }

    /** 그날 0시 (KST). {@code null}이면 하한 없음이라 그대로 흘린다 */
    static Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(SEOUL).toInstant();
    }

    /** <b>다음 날</b> 0시 (KST) — 상한을 그날까지 <b>포함</b>시키는 방법이다. {@code null}이면 상한 없음 */
    static Instant startOfNextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(SEOUL).toInstant();
    }
}
