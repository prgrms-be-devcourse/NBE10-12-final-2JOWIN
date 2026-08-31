package com.twojo.order;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주문 전환 동시성 검증 — <b>C의 대표 Evidence</b> (11-work-breakdown.md §4).
 *
 * <p>1주차에 선작성한다. 주문 전환 구현이 없어 지금은 {@code @Disabled}이며,
 * 전환 구현 이슈에서 활성화한다 — 지금 켜면 CI build 잡이 실패해 머지가 차단된다
 * (13-dev-workflow.md §3).
 *
 * <p>검증 대상은 §4의 주문 전환 트랜잭션이다.
 * <pre>
 * BEGIN
 *   SELECT ... FROM quote WHERE id = ? FOR UPDATE
 *   status = APPROVED 검증                    -- 아니면 QUOTE_NOT_APPROVED (OD-02)
 *   orders INSERT (금액·항목 값 복사)          -- 중복이면 UNIQUE(quote_id) 위반
 *                                             -- → QUOTE_ALREADY_CONVERTED (OD-03)
 *   deal.stage = WON (이미 WON이면 유지 — 멱등) -- OD-06, Q-25
 *   OrderCreated 이벤트 발행                   -- AC-07
 * COMMIT
 * </pre>
 */
@Disabled("주문 전환 구현 이슈에서 활성화 — TODO: 이슈 번호 기입")
class OrderConversionConcurrencyTest {

    @Test
    @DisplayName("동일 견적 100건 동시 전환 → 주문 1건만 생성된다 (OD-03)")
    void 동시_전환은_1건만_성공한다() {
        // given: APPROVED 견적 1건
        // when : 같은 quoteId로 convert-to-order를 100 스레드에서 동시 호출
        //        (CountDownLatch로 출발선을 맞춘다 — 순차 실행이 되면 검증이 무의미)
        // then : 성공 1건 · 나머지 99건은 QUOTE_ALREADY_CONVERTED
        //        orders 테이블의 해당 quote_id 행이 정확히 1개
        //        UNIQUE(quote_id)가 최종 방어선이므로 FOR UPDATE가 빠져도 이 단언은 통과해야 한다
    }

    @Test
    @DisplayName("이미 성사된 Deal의 두 번째 승인 견적도 전환된다 — 멱등 (Q-25)")
    void 이미_WON인_딜은_상태를_유지한다() {
        // given: 이미 WON인 Deal + 승인된 두 번째 견적
        // when : convert-to-order 호출
        // then : 주문이 추가 생성되고 Deal은 WON 유지
        //        DEAL_ALREADY_WON을 던지지 않는다 (검증 노트 #2)
    }
}
