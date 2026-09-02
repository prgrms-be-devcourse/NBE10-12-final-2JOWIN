package com.twojo.boundary;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Deal 조회 계약 — 구현: C(deal 모듈). B·D는 deal 테이블을 직접 조회하지 않는다.
 * (docs/11-work-breakdown.md §4)
 */
public interface DealQuery {

    /**
     * 담당 구성원 (D 알림 수신자 결정 Q-26 · 열람 페이지 현재 담당자 AP-18 · B 접근 판정).
     *
     * <p><b>살아있는 Deal은 담당자가 항상 있다</b> — {@code deal.assignee_member_id}가
     * {@code NOT NULL}이고 복합 FK로 member를 참조한다. 소비자는 null을 방어하지 않아도 된다.
     *
     * <p>없거나 소프트 삭제된 Deal이면 {@code RESOURCE_NOT_FOUND}를 던진다 —
     * "누구에게 알릴 것인가"에 답이 없으면 호출자가 진행할 수 없기 때문이다.
     * <b>{@link #isOpen}이 같은 상황에서 {@code false}를 돌려주는 것과 다르다</b>:
     * 거기서는 "여기에 견적을 더 붙여도 되는가"를 묻고, 없는 Deal의 답은 자연스럽게 "안 된다"이다.
     */
    UUID assigneeIdOf(UUID dealId);

    /**
     * 진행 중(리드~협상) 여부.
     *
     * <p><b>없거나 소프트 삭제된 Deal은 {@code false}다</b> (예외 아님) — 위 {@code assigneeIdOf}와
     * 다루는 방식이 다른 이유는 그 javadoc에 있다.
     */
    boolean isOpen(UUID dealId);

    /** B의 CU-08 판정 — 고객사 삭제 차단 (v2.0.1 보강) */
    boolean hasOpenDeals(UUID customerId);

    /** B의 CU-12 — 고객사 상세 Deal 이력 (v2.0.1 보강) */
    List<DealSummary> summariesByCustomer(UUID customerId);

    /**
     * Deal id 묶음 → 요약 배치 조회 — B의 최근 활동(DB-04)·후속 필요(DB-05) 줄마다 붙는
     * 딜 제목의 유일한 창구다. {@code activity}·{@code task}에는 {@code deal_id}만 있다.
     *
     * <p><b>줄마다 호출하지 않게 배치로 받는다</b> — 목록 20건이면 조회도 20번이 된다.
     * 반환은 요청 순서를 보장하지 않으므로 호출자가 id로 인덱싱한다.
     * 없는 id는 결과에서 빠진다(예외 아님) — 소프트 삭제된 Deal의 활동이 남아 있을 수 있다.
     * 빈 목록을 넘기면 빈 목록을 돌려준다.
     *
     * <p>{@link DealSummary#stage}로 호출자가 종결 Deal을 걸러낼 수 있어 별도 플래그를 두지 않는다.
     * {@link DealSummary#wonAmount}는 주문 합계 계산(DL-18)이 따라붙는다 —
     * 제목·단계만 필요한 자리에서도 함께 계산된다.
     */
    List<DealSummary> summariesByIds(UUID companyId, Collection<UUID> dealIds);

    /**
     * 담당 Deal id 전체 — B의 활동·할 일 집계(DB-04·05)를 SC-02 범위로 거르는 데 쓴다.
     *
     * <p><b>{@code scope == OWNED_ONLY}일 때만 호출한다.</b> 기업 관리자(COMPANY_ALL)는 회사 범위로
     * 조회하면 되고, 여기서 회사 전체 Deal id를 받으면 IN 절만 비대해진다.
     *
     * <p>소프트 삭제된 Deal은 <b>제외</b>하고, 종결(WON·LOST) Deal은 <b>포함</b>한다 —
     * 최근 활동(DB-04)에는 성사된 딜의 상담 이력도 나와야 하기 때문이다.
     * 진행 중만 필요하면 호출자가 한 번 더 거른다.
     *
     * <p>{@code companyId}를 명시적으로 받는다. 한 사람은 한 회사에만 속하므로(Q-14) memberId만으로도
     * 회사가 정해지지만, SC-01 격리는 다른 규칙에서 <b>추론</b>하지 않고 인자로 <b>명시</b>한다.
     */
    List<UUID> assignedDealIds(UUID companyId, UUID memberId);

    record DealSummary(UUID id, String title, String stage,
                       Long expectedAmount, Long wonAmount, Instant createdAt) {}
}
