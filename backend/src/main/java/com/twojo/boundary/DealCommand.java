package com.twojo.boundary;

import java.util.UUID;

/**
 * Deal 상태를 바꾸는 경계 계약 — <b>시스템 전이</b> 전용이다 (docs/11 §7.2).
 *
 * <p>담당자의 수동 단계 이동(DL-07·08·10~12)은 여기 없다. 그것은 구성원 요청이라
 * {@code DealController}가 직접 받고, 회사 스코프·담당 축 판정이 함께 걸린다.
 * 이 계약에 오는 것은 <b>다른 도메인에서 일어난 사건의 효과</b>로 Deal이 움직이는 경우다
 * (전이표 §5의 "행위자: 시스템" 행).
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
}
