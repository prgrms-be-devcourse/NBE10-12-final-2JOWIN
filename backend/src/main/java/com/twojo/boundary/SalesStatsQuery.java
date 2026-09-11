package com.twojo.boundary;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * 대시보드 집계 계약 — 구현: C. D가 소비한다 (DB-01~08, v2.0.1 보강. SC 범위는 ctx로 적용).
 * <p>집계도 이 인터페이스 경유가 원칙 — deal·quote·orders 직접 조회 금지.
 * 성능 문제가 확인되면 읽기 전용 뷰 허용 여부를 팀 합의로 결정한다. (docs/11-work-breakdown.md §4)
 */
public interface SalesStatsQuery {

    /** DB-01 — 진행 단계(리드~협상) 기준 */
    List<StageCount> pipeline(AccessContext ctx);

    /** DB-02 — 주문 합계 (DL-18) */
    WonStats monthlyWon(AccessContext ctx, YearMonth month);

    /** DB-06·08 — 기업 관리자 전용 */
    List<MemberPerformance> performance(UUID companyId, LocalDate from, LocalDate to);

    /**
     * DB-07 — 단계별 전환율. <b>도달 기준</b>이다 (2026-09-11 D 확정, #307).
     *
     * <pre>
     * 단계 순서: LEAD &lt; CONSULT &lt; QUOTE &lt; NEGOTIATION &lt; WON   (LOST는 순서 밖)
     * 도달(S)   = 그 딜이 S 이상 단계에 도달한 적 있음
     * rate(X→Y) = |도달(Y) 고유 딜| / |도달(X) 고유 딜|      (도달(X)=0이면 0)
     * </pre>
     *
     * <p><b>모집단은 기간 안에 등록된 딜이다</b> — 전이 이력이 아니다. 이력에는 움직인 딜만 남아
     * 리드에 멈춘 딜이 분모에서 빠지고, 그러면 수치가 늘 1 근처가 된다. 기간은 "이때 들어온 딜이
     * 어디까지 갔나"로 읽는다 — 코호트다. 기간은 한국 날짜이고 <b>양 끝을 포함</b>한다.
     *
     * <p><b>항상 인접 네 쌍이 순서대로 온다</b> — {@code LEAD→CONSULT}·{@code CONSULT→QUOTE}·
     * {@code QUOTE→NEGOTIATION}·{@code NEGOTIATION→WON}. 딜이 한 건도 없는 기간도 빈 목록이
     * 아니라 {@code rate} 0인 네 행이다 — 화면의 네 칸이 흔들리지 않게 한다.
     * {@code rate}는 0~1 소수이고 셋째 자리에서 반올림한다 (#85 합의).
     *
     * <p>되돌리기(DL-08)·왕복은 모델 안에서 접힌다 — 도달은 내려가지 않고 딜은 고유하게 센다.
     * 자동 승급(Q-25)·자동 성사(OD-06)도 최고 도달에 자연히 반영돼 역전이 쌍은 나오지 않는다.
     *
     * <p><b>되돌린 딜의 봉우리만 {@code audit_log}에 기댄다</b> — 나머지는 {@code deal}이 답한다.
     * 리스너(#303) 이전에 되돌려진 딜은 그 봉우리를 복원할 수 없어 그만큼 낮게 나온다.
     * 화면에서 "집계 준비 중"을 언제 걷을지는 소비처가 판단한다.
     */
    List<StageConversion> conversions(UUID companyId, LocalDate from, LocalDate to);

    record StageCount(String stage, int count, Long expectedAmountSum) {}

    record WonStats(Long amount, int count) {}

    record MemberPerformance(UUID memberId, String name,
                             int wonCount, Long wonAmount, int activeDealCount) {}

    record StageConversion(String fromStage, String toStage, double rate) {}
}
