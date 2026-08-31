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
 *
 * <p><b>{@link AccessScope#OWNED_ONLY} 구현에는 선행 계약이 필요하다.</b> 그 판정은
 * "이 구성원이 담당하는 Deal 집합"을 알아야 하는데, 현재 {@code DealQuery}에는 그것을 주는
 * 조회가 없다 — {@code assigneeIdOf}는 Deal 하나의 담당자를 되돌려줄 뿐이다.
 * {@code activity.author_member_id}로 대신할 수도 없다: 담당이 바뀌어도 이전 담당자가 남긴
 * 상담 기록은 계속 보여야 하므로(AC-08 · PB-05) 작성자는 조회 축이 아니다.
 * 이 계약이 생기기 전에는 {@code OWNED_ONLY} 범위를 만족하는 구현이 나오지 않는다.
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
     * @param ctx   접근 컨텍스트 — 회사(SC-01)·역할·담당 범위 판정에 쓴다
     * @param limit 1 이상 {@link #MAX_LIMIT} 이하. 범위를 벗어나면
     *              {@code IllegalArgumentException} — 이 값은 사용자 입력이 아니라
     *              호출부(D)가 정하는 상수이므로, 잘라내 넘기는 대신 프로그래밍 오류로 드러낸다
     * @return 정렬과 {@code limit}이 적용된 대시보드 요약 목록. 해당 활동이 없으면 빈 목록
     */
    List<RecentActivitySummary> recent(AccessContext ctx, int limit);

    /**
     * 대시보드 최근 활동 한 줄.
     *
     * @param dealId     화면의 이동 대상 — 제목 조립은 소비자(D)가 한다
     * @param summary    카드 한 줄에 실을 <b>표시용 요약</b> — 채널 표기 + 내용이다.
     *                   {@code activity.content}는 길이 제한이 없는 text라 <b>원문을 그대로 싣지
     *                   않는다.</b> 자르는 길이와 채널 표기 형식은 아직 이 계약에 없다 —
     *                   소비자가 카드 폭을 잡으려면 필요하므로, 정해지는 대로 여기 명시한다
     * @param occurredAt 활동 발생 시각 (기록 시각이 아니다, AC-01)
     */
    record RecentActivitySummary(UUID dealId, String summary, Instant occurredAt) {}
}
