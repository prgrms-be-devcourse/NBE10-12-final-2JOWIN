package com.twojo.boundary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 할 일 조회 계약 — 구현: B(activity 모듈). D의 대시보드가 소비한다 (DB-05).
 *
 * <p>11 §7.2 인터페이스 목록의 공백을 메운다. {@code task}는 B 소유이고(§3),
 * D는 그 테이블을 직접 조회하지 않는다.
 *
 * <p><b>"내 할 일"의 정의는 Deal에서 파생된다</b> — task에는 배정 컬럼이 없다(Q-29, §3).
 * 담당 판정 축은 {@code deal.assignee_member_id} 하나뿐이므로(§1.4), 범위 판정은
 * {@code ctx}가 그대로 옮긴다. 그 판정이 Deal 조회를 필요로 하면 {@code DealQuery} 경유다 —
 * <b>B가 deal 테이블이나 Repository를 직접 보지 않는다</b>(§7.3).
 * {@code dealTitle}을 여기 넣지 않는 이유는 {@link ActivityQuery}와 같다.
 *
 * <p><b>{@link AccessScope#OWNED_ONLY} 구현에는 선행 계약이 필요하다.</b> 그 판정은
 * "이 구성원이 담당하는 Deal 집합"을 알아야 하는데, 현재 {@code DealQuery}에는 그것을 주는
 * 조회가 없다 — {@code assigneeIdOf}는 Deal 하나의 담당자를 되돌려줄 뿐이라 목록을 거르는 데
 * 쓰려면 Deal을 이미 알고 있어야 한다. 이 계약이 생기기 전에는 {@code OWNED_ONLY} 범위를
 * 만족하는 구현이 나오지 않는다.
 */
public interface TaskQuery {

    /**
     * {@code limit}의 상한 — 대시보드 카드가 보여줄 건수를 넘어설 이유가 없다.
     * 무제한 목록 반환은 계약으로 막는다(§7.3).
     */
    int MAX_LIMIT = 50;

    /**
     * DB-05 — 후속 조치가 필요한 할 일. 미완료({@code done_at IS NULL})만.
     *
     * <p><b>정렬</b>: {@code dueDate} 오름차순(임박 먼저). 같은 날짜면 {@code taskId}
     * 오름차순으로 안정화한다 — 동률에서 순서가 흔들리면 새로고침마다 목록이 바뀐다.
     *
     * <p><b>범위</b>: {@code ctx}로 SC 범위를 적용한다 — 기업 관리자는 회사 전체(SC-05),
     * 영업 담당자는 본인 담당 Deal의 할 일만(SC-02·04).
     *
     * @param ctx   접근 컨텍스트 — 회사(SC-01)·역할·담당 범위 판정에 쓴다
     * @param limit 1 이상 {@link #MAX_LIMIT} 이하. 범위를 벗어나면
     *              {@code IllegalArgumentException} — 이 값은 사용자 입력이 아니라
     *              호출부(D)가 정하는 상수이므로, 잘라내 넘기는 대신 프로그래밍 오류로 드러낸다
     * @return 정렬과 {@code limit}이 적용된 대시보드 요약 목록. 미완료 할 일이 없으면 빈 목록
     */
    List<FollowUpSummary> followUps(AccessContext ctx, int limit);

    /**
     * 대시보드 후속 조치 한 줄.
     *
     * @param taskId  완료 처리의 대상 (AC-09)
     * @param dealId  화면의 이동 대상 — 제목 조립은 소비자(D)가 한다
     * @param content 할 일 내용
     * @param dueDate 마감일 — 항상 존재한다. {@code task.due_date}가 NOT NULL이고
     *                생성 요청도 필수라(AC-09) 기한 없는 할 일은 만들어지지 않는다
     */
    record FollowUpSummary(UUID taskId, UUID dealId, String content, LocalDate dueDate) {}
}
