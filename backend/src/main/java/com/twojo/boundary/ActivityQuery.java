package com.twojo.boundary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 활동 이력 조회 계약 — 구현: B(activity 모듈). D의 대시보드가 소비한다 (DB-04).
 *
 * <p>11 §7.2 인터페이스 목록의 공백을 메운다. DB-04("최근에 일어난 활동")는 B 소유
 * {@code activity} 테이블이 원천인데 D가 그것을 읽을 통로가 없었다
 * (docs/11-work-breakdown.md §3 "B·C·D는 남의 테이블을 직접 조회하지 않는다").
 *
 * <p><b>반환에 {@code dealTitle}이 없는 이유</b> — Deal 제목은 C 소유다(§4). 여기 넣으면
 * B가 deal 테이블이나 그 Repository를 직접 조회해야 하고, 그것이 §7.3이 금지하는 경로다.
 * 08-dto.md의 {@code DashboardSummaryResponse.RecentActivity}에는 {@code dealTitle}이 있으므로
 * <b>제목 조달은 소비자(D)의 조립 책임</b>이다 — C의 {@code DealQuery}를 거친다.
 */
public interface ActivityQuery {

    /**
     * {@code limit}의 상한 — 대시보드 카드가 보여줄 건수를 넘어설 이유가 없다.
     * 무제한 목록 반환은 계약으로 막는다(§7.3).
     */
    int MAX_LIMIT = 50;

    /**
     * DB-04 — 최근 활동.
     *
     * <p><b>정렬</b>: {@code occurredAt} 내림차순(최신 먼저). 같은 시각이면 {@code id} 오름차순으로
     * 안정화한다 — 동률에서 순서가 흔들리면 새로고침마다 목록이 바뀐다.
     * 기준 시각은 <b>기록 시각이 아니라 활동 발생 시각</b>이다(AC-01).
     *
     * <p><b>범위</b>: {@code ctx}로 SC 범위를 적용한다 — 기업 관리자는 회사 전체(SC-05),
     * 영업 담당자는 본인 담당 Deal의 활동만(SC-02·04). 삭제된 활동(soft delete)은 제외한다.
     *
     * @param limit 1 이상 {@link #MAX_LIMIT} 이하. 범위를 벗어나면
     *              {@code IllegalArgumentException} — 이 값은 사용자 입력이 아니라
     *              호출부(D)가 정하는 상수이므로, 잘라내 넘기는 대신 프로그래밍 오류로 드러낸다
     */
    List<RecentActivitySummary> recent(AccessContext ctx, int limit);

    /**
     * 대시보드 최근 활동 한 줄.
     *
     * @param dealId     화면의 이동 대상 — 제목 조립은 소비자(D)가 한다
     * @param summary    표시 문구 (channel + content 요약)
     * @param occurredAt 활동 발생 시각 (기록 시각이 아니다, AC-01)
     */
    record RecentActivitySummary(UUID dealId, String summary, Instant occurredAt) {}
}
