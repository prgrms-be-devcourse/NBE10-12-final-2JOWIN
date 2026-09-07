package com.twojo.boundary;

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
     * 구성원 비활성화에 따른 담당 Deal 일괄 이관 (MB-14, Q-29) — A의 비활성화 API가 부른다.
     *
     * <p>{@code fromMemberId}가 담당인 <b>진행 중(리드~협상)</b> Deal 전부를 {@code toMemberId}로 옮긴다.
     * 소프트 삭제된 Deal은 대상이 아니다. 담당 Deal이 0건이면 아무 일도 하지 않는다 — 예외가 아니다.
     * 건수를 돌려주지 않는다: 호출자는 {@link DealQuery#assignedDealIds}로 이관이 필요한지 <b>먼저</b>
     * 판정하고(07 §A: 담당 Deal이 있는데 이관 대상이 없으면 422 {@code MEMBER_INACTIVE_TRANSFER_REQUIRED}),
     * 여기는 그 뒤에 실행만 한다.
     *
     * <p><b>종결(WON·LOST) Deal은 그대로 둔다</b> — 담당자 이력이다. 그 결과 성사 Deal의 담당자가
     * 비활성 구성원으로 남고, SC-02 때문에 그 Deal은 기업 관리자만 다룰 수 있게 된다
     * (성사 후 두 번째 승인 견적의 주문 추가 전환도 마찬가지, Q-25). 이 선택은 #130 「리뷰 필요」 1번이다 —
     * 종결 Deal까지 옮기기로 정해지면 이 문단과 구현이 같이 바뀐다.
     *
     * <p>할 일(task)은 따로 옮기지 않는다 — 배정 컬럼이 없어 Deal을 따라 자동으로 옮겨간다 (Q-29).
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
     * <p>감사 기록의 단위(Deal마다 한 건 / {@code MEMBER_DEACTIVATED} 한 건의 changes에 묶음)는
     * 구현자(C)가 정한다 — #130 「리뷰 필요」 2번.
     */
    void reassignAll(UUID companyId, UUID fromMemberId, UUID toMemberId);
}
