package com.twojo.boundary;

import java.util.List;
import java.util.UUID;

/**
 * 견적 상태 변경 계약 — 구현: C(quote 모듈). D가 호출한다.
 * <p><b>D는 quote 상태를 직접 바꾸지 않는다</b> — 상태 변경 주체는 항상 C (docs/11-work-breakdown.md §4·§7.1).
 * approve·reject는 토큰 소진(AP-11)과 한 트랜잭션으로 묶인다 (D 주관).
 */
public interface QuoteCommand {

    /** GET /public/quotes/{token} 첫 열람 (AP-06·07) */
    void markViewed(UUID quoteId);

    /** AP-08·19 — 토큰 소진과 한 트랜잭션 (D 주관) */
    void approve(UUID quoteId, Responder responder);

    void reject(UUID quoteId, String reason, Responder responder);

    /**
     * 주문 전환을 위해 견적 행을 <b>잠그고</b> 스냅샷을 떠 준다 (OD-01·02·04).
     *
     * <p><b>이름에 lock이 들어간 것이 계약의 핵심이다.</b> {@code SELECT ... FOR UPDATE}로
     * 견적 행을 잡고, 그 락은 <b>호출자가 커밋할 때까지</b> 유지된다. 그래서 같은 견적을
     * 동시에 100번 전환해도 "이미 주문이 있는가" 검사가 직렬화되고, 진 쪽이
     * {@code QUOTE_ALREADY_CONVERTED}를 받는다. 락이 없으면 그 검사를 전부 통과해
     * {@code orders.quote_id UNIQUE}가 터지고, 그건 409가 아니라 <b>500</b>이다.
     *
     * <p>그래서 <b>{@code Propagation.MANDATORY}</b>다 — 트랜잭션 밖에서 부르면 락이 즉시
     * 풀려 아무것도 막지 못하므로, 조용히 무력해지는 대신 그 자리에서 실패한다
     * ({@code DocumentNumberService.next}와 같은 이유, #72).
     *
     * <p><b>여기서 보는 것은 상태뿐이다</b> — 승인됨(APPROVED)이 아니면
     * {@code QUOTE_NOT_APPROVED}, 없거나 다른 회사면 {@code RESOURCE_NOT_FOUND}.
     * <b>담당 축(SC-04) 판정은 호출자가 먼저 한다</b>: 남의 딜 견적에 409를 돌려주면
     * "그 견적은 있는데 아직 승인 전"이라는 사실이 새어 나간다 (SC-09).
     */
    ConversionSnapshot lockApprovedForConversion(UUID companyId, UUID quoteId);

    /** v2.0.2 — 자기 신고 신원 (Q-44), title은 null 허용 */
    record Responder(String name, String title) {}

    /**
     * 전환 시점의 견적 — <b>주문이 값으로 복사해 갈 전부</b>다 (OD-04).
     *
     * <p>주문은 이 값을 자기 테이블에 넣고 다시는 견적을 보지 않는다. 그래서 전환 뒤
     * 견적을 고쳐도 주문은 그대로다 (OD-05) — 주문은 그 시점의 합의를 고정하는 문서다.
     */
    record ConversionSnapshot(UUID quoteId, String quoteNo, UUID dealId,
                              Long supplyAmount, Long vatAmount, Long totalAmount,
                              List<Line> items) {

        /** 견적 항목의 값 복사본 — {@code product}·{@code quote_item} 어느 쪽으로도 FK를 걸지 않는다 */
        public record Line(String name, String unit, int quantity, Long unitPrice, Long amount) {}
    }
}
