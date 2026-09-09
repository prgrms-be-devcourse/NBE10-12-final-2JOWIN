/**
 * 서버가 아직 세지 않는 집계 — `SalesStatsQueryImpl`의 자리표시자 (C 후속 #216).
 *
 * `monthlyWon`은 `0`, `performance`·`conversions`는 빈 목록을 돌려준다. 응답만으로는
 * **"진짜 0"과 구분할 수 없어서** 화면이 그대로 그리면 실적을 0으로 오해한다.
 * 막힌 이유는 둘이다 — 성사 금액은 orders 경계 창구가, 전환율은 단계 전이 이력이 없다.
 *
 * #216이 머지되면 이 파일을 지우고 참조를 걷어낸다. 한쪽만 먼저 풀리면 그때 둘로 가른다.
 */
export const SALES_STATS_PENDING = true

/** 자리표시자 자리에 0 대신 쓰는 문구 — "없다"가 아니라 "아직 안 센다"임을 말한다. */
export const PENDING_LABEL = '집계 준비 중'
