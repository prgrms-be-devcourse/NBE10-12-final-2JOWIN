/**
 * 경계 인터페이스 — 도메인 간 데이터 통로의 계약 (docs/11-work-breakdown.md §7.2 그대로).
 *
 * <p><b>왜 별도 모듈인가</b> — 문서의 인터페이스 그래프는 B↔C(DealQuery ↔ CustomerQuery·ProductQuery),
 * C↔D(ViewTokenCommand ↔ QuoteCommand)로 의도된 양방향이다. 인터페이스를 각 소유 모듈에 두면
 * Spring Modulith가 금지하는 모듈 순환이 되므로, <b>계약은 여기에 두고 구현은 소유 도메인이 한다.</b>
 * 모든 도메인 → boundary 단방향 의존만 남아 순환이 사라진다.
 *
 * <p><b>소유(구현 책임)는 여전히 문서대로다</b>:
 * <ul>
 *   <li>A 조민석 — {@link com.twojo.boundary.MemberQuery}</li>
 *   <li>B 한상민 — {@link com.twojo.boundary.CustomerQuery} · {@link com.twojo.boundary.ProductQuery}
 *       · {@link com.twojo.boundary.ActivityQuery} · {@link com.twojo.boundary.TaskQuery} (DB-04·05 통로)</li>
 *   <li>C 최선진 — {@link com.twojo.boundary.QuoteCommand} · {@link com.twojo.boundary.DealQuery}
 *       · {@link com.twojo.boundary.QuoteQuery} · {@link com.twojo.boundary.SalesStatsQuery}</li>
 *   <li>D 이준형 — {@link com.twojo.boundary.ViewTokenCommand} · {@link com.twojo.boundary.ViewTokenQuery}
 *       · {@link com.twojo.boundary.MailCommand} (메일 예약 통로 — approval·auth가 호출)</li>
 * </ul>
 * 시그니처 변경은 소유자 + 소비자 합의로만 한다.
 */
package com.twojo.boundary;
