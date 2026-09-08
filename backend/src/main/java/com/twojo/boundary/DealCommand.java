package com.twojo.boundary;

import java.util.List;
import java.util.UUID;

/**
 * Deal 상태를 바꾸는 경계 계약 — <b>시스템 전이</b> 전용이다 (docs/11 §7.2).
 *
 * <p>담당자의 수동 단계 이동(DL-07·08·10~12)은 여기 없다. 그것은 구성원 요청이라
 * {@code DealController}가 직접 받고, 회사 스코프·담당 축 판정이 함께 걸린다.
 * 이 계약에 오는 것은 <b>다른 도메인에서 일어난 사건의 효과</b>로 Deal이 움직이는 경우다
 * (전이표 §5의 "행위자: 시스템" 행) — 견적 발송에 따른 승급, 구성원 비활성화에 따른 담당 이관.
 *
 * <p>{@link DealQuery}와 나눈 이유는 트랜잭션 경계다 — 조회 계약은
 * {@code @Transactional(readOnly = true)}이고, 상태를 바꾸는 메서드가 섞이면 그 경계가 흐려진다.
 * {@code QuoteCommand}/{@code QuoteQuery}, {@code ViewTokenCommand}/{@code ViewTokenQuery}가
 * 이미 같은 방식이다.
 */
public interface DealCommand {

    /**
     * 견적 발송에 따른 자동 승급 (Q-25) — 단계가 견적(QUOTE) 미만이면 견적으로 올린다.
     *
     * <p>전이표 §5: "리드·상담 → 견적 발송 → 견적(QUOTE) · <b>시스템</b>".
     * DL-07(한 단계씩)의 예외이고, 주문 전환 시 자동 성사(OD-06)와 같은 성격이다.
     *
     * <p><b>이미 견적·협상이면 아무 일도 하지 않는다</b> — 승급 대상이 아닐 뿐 오류가 아니다.
     * 협상 딜의 견적을 발송하는 것은 정상 시나리오라, 예외로 만들면 발송이 막힌다.
     * 협상을 견적으로 내리지도 않는다.
     *
     * <p><b>종결(WON·LOST) Deal이면 던진다</b> — 성사는 {@code DEAL_ALREADY_WON},
     * 실패는 {@code DEAL_NOT_OPEN}이다. 조용히 무동작하면 "종결된 딜에 견적이 발송됐다"는
     * 모순이 흔적 없이 지나간다. <b>발송을 종결 딜에서 허용할지는 호출자가 먼저 판정한다</b> —
     * 여기까지 왔다는 것은 그 판정이 없었거나 뚫렸다는 뜻이다.
     *
     * <p>없거나 소프트 삭제된 Deal이면 {@code RESOURCE_NOT_FOUND}.
     * <b>호출자의 트랜잭션에 합류한다</b> — 발송이 롤백되면 단계도 함께 되돌아가야 한다.
     */
    void promoteToQuoteStage(UUID dealId);

    /**
     * 구성원 비활성화에 따른 담당 Deal 이관 (MB-14, Q-29) — A의 비활성화 API가 부른다.
     *
     * <p>{@code fromMemberId}가 담당인 <b>진행 중(리드~협상)</b> Deal 전부를 {@code toMemberId}로 옮긴다.
     * 이름이 "Open"인 이유가 그것이다 — <b>종결(WON·LOST) Deal은 그대로 둔다</b>(아래).
     * 소프트 삭제된 Deal도 대상이 아니다. 옮길 Deal이 0건이면 아무 일도 하지 않는다 — 예외가 아니다.
     *
     * <p><b>이관이 필요한지는 호출자가 {@link DealQuery#countOpenAssigned}로 먼저 판정한다</b>
     * (07 §A: 진행 중 담당 Deal이 있는데 이관 대상이 없으면 422 {@code MEMBER_INACTIVE_TRANSFER_REQUIRED}).
     * {@link DealQuery#assignedDealIds}로 판정하면 안 된다 — 그쪽은 종결 Deal을 <b>포함</b>하므로
     * 종결 Deal만 남은 구성원이 이관 대상 없이는 비활성화되지 못한다.
     *
     * <p><b>옮긴 Deal id를 돌려준다</b> — 감사는 Deal마다 한 건이 아니라 {@code MEMBER_DEACTIVATED}
     * 한 건의 payload에 {@code dealIds}로 묶는다(#130 「리뷰 필요」 2번, C·E 합의). 그 감사 행을 쓰는 쪽은
     * 호출자(A)라 여기서 id를 넘겨줘야 한다. 호출자는 사전 판정 건수와 반환 건수가 같은지 단언해도 된다.
     *
     * <p><b>종결 Deal을 남기는 이유와 대가</b> — {@code deal.assignee_member_id}는 담당자별 성과 집계의
     * 유일한 축이라, 종결 Deal까지 옮기면 퇴사자의 성사 실적이 후임 것이 되고 복구할 수 없다. 대신
     * 성사 Deal의 담당자가 비활성 구성원으로 남는다: SC-02 때문에 그 Deal은 기업 관리자만 다루고
     * (성사 후 두 번째 승인 견적의 주문 추가 전환도 마찬가지, Q-25), 응답 완료 뒤에도 열람이 허용되는
     * 성사 Deal의 열람 페이지는 비활성 담당자의 연락처를 표시한다(AP-18 — {@code MemberQuery#getContact}는
     * 비활성도 그대로 돌려준다). 알림은 새지 않는다 — Q-26 폴백({@code NotificationCommandImpl})이
     * 비활성 담당자를 걸러 기업 관리자에게만 보낸다. 03 §3 Q-48로 등재했다.
     *
     * <p>할 일(task)은 따로 옮기지 않는다 — 배정 컬럼이 없어 Deal을 따라 자동으로 옮겨간다 (Q-29).
     * 상담 기록도 담당 축으로 조회되므로 새 담당자가 그대로 읽는다 (AC-08).
     *
     * <p><b>{@code toMemberId} 검증은 호출자 몫이다</b> — 같은 회사의 활성 구성원인지를
     * {@link MemberQuery#isActive}와 회사 대조로 비활성화 서비스(A)가 먼저 본다(위반 시 404, SC-09).
     * 여기서는 다시 보지 않는다 — 수신인 검증을 발송 쪽(C)이 맡고 {@code ViewTokenCommand#issue}가
     * 방어 체크를 두지 않는 것과 같은 규약이다. {@code companyId}는 SC-01 격리를 인자로 <b>명시</b>하기
     * 위해 받는다 — {@code fromMemberId}만으로도 회사가 정해지지만(Q-14) 추론하지 않는다.
     *
     * <p><b>호출자의 트랜잭션에 합류한다</b> — 비활성화가 롤백되면 이관도 되돌아간다.
     * "구성원은 비활성인데 Deal은 그대로"나 그 반대는 존재하면 안 되는 상태다 (11 §2 "한 트랜잭션").
     *
     * <p><b>구현 규약</b>: 엔티티를 경유해 옮긴다 — JPQL 일괄 update는 {@code @Version}과
     * {@code updated_at}을 건드리지 않아, 열어 둔 딜 상세의 낙관적 락(DL-05)이 이관을 알아채지 못한다.
     * 구성원당 몇 건이라 성능은 문제가 아니다.
     *
     * <p><b>알려진 공백(v1)</b>: 사전 판정과 이관 사이에 잠금이 없다. 그 사이 이 구성원에게 Deal이 새로
     * 배정되면 비활성 담당자가 남을 수 있다. 배정 쪽 검증({@code MemberQuery#isActive})도 비활성화 커밋
     * 전에는 활성으로 답한다. 드물고 관리자가 담당자 변경(DL-05)으로 바로잡을 수 있어 v1에서는 받아들인다.
     *
     * @return 옮긴 Deal id — 0건이면 빈 목록. 순서는 보장하지 않는다
     */
    List<UUID> reassignOpenDeals(UUID companyId, UUID fromMemberId, UUID toMemberId);
}
