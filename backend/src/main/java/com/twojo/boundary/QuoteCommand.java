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
     * <p><b>호출자의 트랜잭션은 쓰기여야 한다.</b> {@code MANDATORY}는 트랜잭션의 <b>존재만</b>
     * 확인하고 읽기 전용 여부까지 보지 않는다 — 호출자가 {@code @Transactional(readOnly = true)}면
     * 이 검사를 통과하고, 그 트랜잭션에서 Hibernate가 flush를 건너뛰어 <b>주문 생성이 예외 없이
     * 사라진다.</b> 클래스 레벨 {@code readOnly} 위에 메서드 레벨 {@code @Transactional}을
     * 빠뜨린 경우가 특히 그렇다 (PR #197 리뷰).
     *
     * <p><b>여기서 보는 것은 상태뿐이다</b> — 승인됨(APPROVED)이 아니면
     * {@code QUOTE_NOT_APPROVED}, 없거나 다른 회사면 {@code RESOURCE_NOT_FOUND}.
     * <b>담당 축(SC-04) 판정은 호출자가 먼저 한다</b>: 남의 딜 견적에 409를 돌려주면
     * "그 견적은 있는데 아직 승인 전"이라는 사실이 새어 나간다 (SC-09).
     */
    ConversionSnapshot lockApprovedForConversion(UUID companyId, UUID quoteId);

    /**
     * 딜 실패 시 그 딜의 진행 중 견적과 열람 링크를 닫는다 (DL-10, 전이표 §5).
     *
     * <p><b>실패 처리의 효과이지 별도 기능이 아니다.</b> DL-10이 "실패 처리하면 진행 중이던 견적과
     * 열람 링크는 만료된다"로 규정하고, 전이표 §5가 그 결과로 "승인 경로가 닫힘"을 적는다.
     * 이것이 없으면 담당자가 딜을 접은 뒤에도 고객이 살아 있는 링크로 <b>승인할 수 있고</b>,
     * 그 승인은 주문 전환에서 {@code DEAL_NOT_OPEN}으로 막혀 고객과 담당자가 다른 사실을 본다.
     *
     * <p><b>닫는 대상은 발송됨·열람됨뿐이다.</b> 작성 중(DRAFT)은 보낸 적이 없어 닫을 것이 없고,
     * 승인·반려는 이미 응답이 끝났으며, 회수·기간 만료는 이미 닫혔다. 판정은
     * {@code Quote.expire()}가 하고 여기서는 딜에 걸린 견적을 모아 넘긴다 —
     * 기간 만료 배치(Q-37)와 <b>같은 메서드</b>를 써서 규칙이 두 벌이 되지 않게 한다.
     *
     * <p>딜 하나에 견적이 여럿일 수 있다 (QT-18) — 전부 닫는다. 닫을 것이 없으면 아무 일도
     * 하지 않는다(no-op) — 견적을 만들지 않은 딜을 실패 처리하는 것은 정상이다.
     *
     * <p><b>{@code MANDATORY}다.</b> 실패 처리와 <b>한 트랜잭션</b>이어야 한다 —
     * 딜만 LOST이고 견적 일부가 열린 상태는 전이표에 없고, 그게 남으면 지금 고치려는 문제가
     * 그대로 재현된다. 기간 만료 배치가 건별 {@code REQUIRES_NEW}로 격리하는 것과 갈리는 이유는
     * <b>대량 처리가 아니기 때문</b>이다 — 이쪽은 담당자 요청 한 건이라 부분 성공이 의미가 없다.
     * 링크 만료({@code ViewTokenCommand.expire})도 같은 트랜잭션에 합류한다.
     *
     * <p><b>회사 스코프를 걸지 않는다.</b> 호출자가 이미 회사 안에서 얻은 dealId를 넘기는 자리이고,
     * 그 경로에서 SC-01·02 판정이 끝나 있다 — {@code DealCommand}의 시스템 전이와 같은 규약이다.
     */
    void expireOnDealLost(UUID dealId);

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

        /**
         * 견적 항목의 값 복사본 — {@code product}·{@code quote_item} 어느 쪽으로도 FK를 걸지 않는다.
         *
         * <p>{@code sortOrder}가 함께 오는 이유: FK가 없으니 <b>순서도 여기서 받지 않으면 알 방법이 없다</b>.
         * 주문 항목은 이 값으로 정렬되고(QT-07 → OD-04), 없으면 조회할 때마다 순서가 갈린다 (V301).
         */
        public record Line(String name, String unit, int quantity,
                           Long unitPrice, Long amount, int sortOrder) {}
    }
}
