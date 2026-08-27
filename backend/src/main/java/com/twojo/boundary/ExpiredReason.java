package com.twojo.boundary;

/**
 * 열람 링크 만료 사유 — {@code quote_view_token.expired_reason}의 CHECK 값과 1:1 (전이표 §7).
 *
 * <p>String이면 오타가 컴파일에 잡히지 않고 DB CHECK 제약에서야 터진다.
 * C·D 합의(2026-08-27)로 타입화했다.
 *
 * <p><b>값을 바꾸면 마이그레이션도 함께 가야 한다</b> —
 * {@code V1__baseline.sql}의 CHECK와 어긋나는 순간 저장이 실패한다.
 *
 * <p><b>누가 넘기는가</b>
 * <table>
 *   <tr><th>값</th><th>호출자</th><th>근거</th></tr>
 *   <tr><td>{@link #TIME}</td><td>C — 견적 만료 배치</td><td>Q-37</td></tr>
 *   <tr><td>{@link #WITHDRAWN}</td><td>C — 견적 회수</td><td>QT-17</td></tr>
 *   <tr><td>{@link #DEAL_LOST}</td><td>C — Deal 실패</td><td>DL-10 · 전이표 §5</td></tr>
 *   <tr><td>{@link #MANUAL}</td><td>D 내부 — 수동 만료 API</td><td>AP-14</td></tr>
 *   <tr><td>{@link #RESENT}</td><td>D 내부 — {@code issue()} 재발송</td><td>AP-13</td></tr>
 * </table>
 */
public enum ExpiredReason {

    /** 기간 경과 — 견적 유효기간이 지나 만료 배치가 정리 (Q-37, 배치 소유는 C) */
    TIME,

    /** 담당자가 링크를 직접 만료 (AP-14) — D의 API 안쪽이라 C는 넘기지 않는다 */
    MANUAL,

    /** 견적 회수 (QT-17) */
    WITHDRAWN,

    /**
     * 수신인 변경 재발송 (AP-13) — 기존 활성 링크를 닫고 새 링크를 연다.
     * <b>{@code issue()} 안에서 D가 붙이는 값</b>이다. C가 {@code expire(RESENT)}를 부르지 않는다.
     */
    RESENT,

    /** Deal 실패로 승인 경로가 닫힘 (DL-10 · 전이표 §5) */
    DEAL_LOST,
}
