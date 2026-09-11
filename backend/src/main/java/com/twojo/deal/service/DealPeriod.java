package com.twojo.deal.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 딜 등록일 → 시각 경계 (DB-07).
 *
 * <p><b>이 규칙의 유일한 자리다.</b> {@code deal.created_at}이 등록 시각이고 그 컬럼은 이 모듈이
 * 소유하므로, 날짜를 시각으로 끊는 규칙도 여기 한 곳에만 둔다 — {@code OrderPeriod}가
 * {@code orders.created_at}에 대해 같은 자리인 것과 같다. 모듈마다 자기 컬럼의 경계를 갖는 것이
 * 규약이고, 그래야 계약이 시간을 스스로 정하지 않는다 ({@code AuditQuery.stageChanges} javadoc).
 *
 * <p><b>한국 날짜로 끊는다</b> — 사람이 읽는 날짜다. 서버 시간대로 끊으면 자정 부근에 등록된 딜이
 * 옆 날짜 코호트로 샌다.
 */
final class DealPeriod {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private DealPeriod() {
    }

    /** 그날 0시 (KST) */
    static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(SEOUL).toInstant();
    }

    /** <b>다음 날</b> 0시 (KST) — 상한을 그날까지 <b>포함</b>시키는 방법이다 */
    static Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(SEOUL).toInstant();
    }
}
