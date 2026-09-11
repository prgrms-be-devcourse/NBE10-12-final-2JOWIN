package com.twojo.activity.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 감사 로그의 날짜 → 시각 경계 (DB-07 · AC-11 목록 필터).
 *
 * <p><b>이 규칙의 유일한 자리다.</b> {@code audit_log.occurred_at}이 이 모듈 소유라 날짜를 시각으로
 * 끊는 규칙도 여기 한 곳에만 둔다. 호출자가 끊어 넘기면 같은 규칙이 모듈마다 한 벌씩 생긴다
 * ({@code OrderPeriod}가 같은 이유로 만들어졌다 — #216 리뷰).
 *
 * <p><b>한국 날짜로 끊는다</b> — 사람이 화면에서 고르는 날짜다. 서버 시간대로 끊으면 자정 부근
 * 전이가 옆 날짜·옆 달로 샌다.
 */
final class AuditPeriod {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private AuditPeriod() {
    }

    /** 그날 0시 (KST) — 하한은 그날을 포함한다. {@code null}이면 하한 없음이라 그대로 흘린다 */
    static Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(SEOUL).toInstant();
    }

    /**
     * <b>다음 날</b> 0시 (KST) — 상한을 그날까지 <b>포함</b>시키는 방법이다.
     *
     * <p>조회는 이 값 <b>미만</b>으로 끊는다. 그날 23:59:59.999999 까지 들어오고, 다음 구간의
     * 하한과 정확히 맞물린다 — 양 끝을 포함하면 경계의 한 건이 두 기간에 다 들어간다.
     * {@code null}이면 상한 없음
     */
    static Instant startOfNextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(SEOUL).toInstant();
    }
}
