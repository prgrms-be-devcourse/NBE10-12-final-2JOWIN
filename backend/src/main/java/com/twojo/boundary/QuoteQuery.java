package com.twojo.boundary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 견적 후보 조회 계약 — 구현: C(quote 모듈). D의 배치·대시보드가 소비한다 (v2.0.1 보강).
 * (docs/11-work-breakdown.md §4)
 */
public interface QuoteQuery {

    /** SENT·VIEWED — NT-05 리마인드 · DB-03 응답 대기 */
    List<QuoteSummary> findAwaitingResponse(UUID companyId);

    /**
     * NT-06 임박 알림 후보 — 유효기간이 {@code from}~{@code to} <b>구간에 드는</b> 발송됨·열람됨 견적
     * ({@code valid_until} 기준, 양 끝 포함).
     *
     * <p><b>전 회사를 한 번에 돌려준다</b> — {@link #findAwaitingResponse}가 회사별인 것과 일부러 다르다.
     * 배치가 회사를 순회하며 부르는 대신 한 번 받아 {@link QuoteSummary#companyId}로 그룹핑하고,
     * 회사당 {@code CompanyQuery.get}을 <b>한 번</b> 불러 정지 여부를 판정한다 (Q-27) — 그래야 한 회사의
     * 조회 실패가 그 회사 견적 수만큼 되풀이되지 않는다 (2026-09-10 D 확정).
     * <b>정지 회사 억제는 호출자가 한다</b> — 배치에는 {@code AccessContext}가 없어 여기서 판정할 수 없다.
     *
     * <p><b>기간을 시각이 아니라 날짜로, 하한까지 받는다.</b> 상한만 받으면 이미 만료된 견적
     * ({@code valid_until} < 오늘)이 후보에 섞이는데, 그 건에 "유효기간이 임박했습니다"를 보내면
     * 틀린 안내다. 견적 만료 배치(Q-37)가 아직 없어 그런 견적이 SENT로 남아 있다.
     * 하한을 이 계약 안에서 "오늘"로 만들지 않는 이유는 규약이다 — 계약은 시간을 스스로 정하지 않고
     * 호출자가 넘긴다 ({@code Quote.requireSendable(today)} · {@code OrderQuery.wonTotalsByQuotes} ·
     * {@code PublicQuoteAssembler} "조립기는 시간을 다루지 않는다"와 같은 규약).
     * 배치가 하루 건너뛰어도 {@code from}을 늘려 잡으면 놓친 건이 다음 실행에 들어온다.
     *
     * <p><b>대상 상태는 발송됨·열람됨뿐이다</b> — {@link #findAwaitingResponse}와 같은 집합이다.
     * 작성 중(DRAFT)은 발송 전이라 알릴 대상이 아니고, 승인·반려는 응답이 끝났고,
     * 회수·기간 만료는 이미 닫혔다 (전이표 §6).
     *
     * <p><b>전역 정렬은 보장하지 않는다</b> — 호출자가 {@code companyId}로 그룹핑하므로 회사 간 순서는
     * 의미가 없고, 회사 안에서만 유효기간 오름차순이다.
     *
     * @param from 유효기간 하한(포함) — 보통 호출 시점의 한국 날짜다
     * @param to   유효기간 상한(<b>포함</b>) — 임박 기준일. 예: 3일 전 알림이면 {@code from.plusDays(3)}
     */
    List<QuoteSummary> findExpiringBetween(LocalDate from, LocalDate to);

    /**
     * 고객 열람 페이지 렌더용 (AP-02·07 · SC-08) — <b>quote 소유 데이터만</b> 준다.
     *
     * <p>C·D 합의 (2026-08-31). D가 조립해야 하는 것은 여기에 담지 않는다 —
     * C가 A의 데이터를 대신 조회해 넘기면 소유자별 책임이 흐려지기 때문이다.
     * <ul>
     *   <li>회사명·사업자번호 → D가 {@link CompanyQuery}로</li>
     *   <li>현재 담당자(AP-18, 발송자 스냅샷 아님) → D가 {@link DealQuery#assigneeIdOf}
     *       + {@link MemberQuery}로</li>
     * </ul>
     *
     * <p>{@link ViewTokenCommand#issue}도 이 메서드로 {@code validUntil}·{@code companyId}를
     * 얻는다 — 그래서 issue의 시그니처를 넓히지 않았다(PR #13 계약 유지).
     * <b>issue 시점의 status는 아직 {@code DRAFT}다</b> — C가 발급 성공 후에 SENT로 바꾸기
     * 때문이다(Q-40 순서 합의). issue 구현에서 SENT를 전제로 검증하면 항상 실패한다.
     *
     * <p>토큰 만료 시각(= validUntil 당일 23:59:59)으로의 변환은 D가 한다.
     *
     * <p><b>없으면 RESOURCE_NOT_FOUND를 던진다</b> — 호출 맥락이 존재를 보장하는 자리라
     * ({@code quote_view_token.quote_id} FK · issue는 C의 발송 트랜잭션 안) 없다는 것은
     * 데이터 이상이다. 호출자는 null을 검사하지 않는다.
     * 404 문구는 SC-09 통일 문구를 따르므로 고객 열람 경로에서도 존재가 노출되지 않는다.
     */
    PublicQuoteView getPublicView(UUID quoteId);

    /**
     * 견적 id 묶음 → 출처 배치 조회 — <b>주문 조회의 유일한 통로다</b> (OD-08·09).
     *
     * <p>{@code orders}에는 {@code deal_id} 컬럼이 없다 (ERD). 주문 응답의 {@code dealId}·
     * {@code quoteNo}는 견적을 거쳐야 나오는데, order 모듈은 quote 테이블을 직접 읽지 않는다
     * (11 §7.3). 줄마다 부르지 않게 배치로 받는다 — 목록 20건이면 조회도 20번이 된다.
     *
     * <p>반환은 요청 순서를 보장하지 않으므로 호출자가 id로 인덱싱한다.
     * 없는 id는 결과에서 빠진다(예외 아님). 빈 목록을 넘기면 빈 목록을 돌려준다.
     */
    List<QuoteOrigin> originsByIds(UUID companyId, Collection<UUID> quoteIds);

    /**
     * 담당 Deal 묶음에 속한 견적 id 전체 — 주문 목록의 <b>범위 필터</b>다 (SC-04, 09 §80).
     *
     * <p>주문의 범위도 {@code deal.assignee_member_id}에서 파생하는데(견적과 같은 축),
     * {@code orders}에서 Deal까지 가려면 quote를 거쳐야 한다. 그 조인을 모듈 밖에서 할 수 없어
     * <b>id 집합으로 받아 {@code quote_id IN (...)}으로 좁힌다</b>.
     *
     * <p><b>{@code scope == OWNED_ONLY}일 때만 호출한다</b> — 기업 관리자는 회사 범위면 충분하다
     * ({@code DealQuery.assignedDealIds}와 같은 규약). 빈 목록을 넘기면 빈 목록을 돌려준다 —
     * 담당 Deal이 하나도 없는 영업이고, 그에게는 주문도 하나도 보이지 않아야 한다.
     */
    List<UUID> quoteIdsByDeals(UUID companyId, Collection<UUID> dealIds);

    /**
     * 딜 상세의 견적 요약 목록 (DL-15) — 딜 묶음에 걸린 견적을 한 줄씩.
     *
     * <p><b>{@link #quoteIdsByDeals}와 갈리는 이유는 소비처다.</b> 그쪽은 주문 목록의 범위
     * 필터라 id만 있으면 되지만, 딜 상세는 화면에 견적번호·상태·금액을 그린다.
     * 같은 조회를 id만 받아 온 뒤 다시 캐물으면 경계를 두 번 지난다.
     *
     * <p>규약은 {@code DealQuery.summariesByIds}와 같다 — <b>순서 보장 없음</b>(호출자가
     * {@code dealId}로 묶는다) · 없는 id는 예외 없이 빠짐 · 빈 묶음이면 빈 목록.
     * 견적에는 소프트 삭제가 없어(상태로 관리) 종결 견적도 그대로 나온다 —
     * 딜 상세는 "이 딜에서 무슨 견적이 오갔나"를 보여주는 자리라 회수·반려도 이력이다.
     */
    List<QuoteBrief> briefsByDeals(UUID companyId, Collection<UUID> dealIds);

    /**
     * 딜 상세에 그리는 견적 한 줄 (DL-15).
     *
     * <p>{@code dealId}를 함께 싣는 이유는 <b>호출자가 딜별로 묶기 위해서</b>다 —
     * 계약이 순서를 보장하지 않으므로 이 축이 없으면 여러 딜을 한 번에 물을 수 없다.
     *
     * <p>{@code totalAmount}는 VAT 포함이다 ({@code OrderQuery.QuoteWonTotal}과 같은 축) —
     * 화면이 견적과 주문 금액을 나란히 놓기 때문이다. 작성 중 견적은 항목이 없으면 null이다.
     * {@code sentAt}은 발송 전이면 null이다.
     */
    record QuoteBrief(UUID id, UUID dealId, String quoteNo, String status,
                      Long totalAmount, Instant sentAt) {}

    /**
     * 응답 대기·만료 임박 견적 한 줄 (NT-05·06, DB-03). {@code firstViewedAt}이 null이면 미열람 (v2.0.2, GAP-08).
     *
     * @param dealId    <b>소비자가 범위를 스스로 거르는 축</b> — 영업 대시보드는 SC-02로 담당 딜만 보여야 하고
     *                  (없으면 누수를 막으려 목록을 통째로 비워야 한다), NT-05 인앱 알림도 이 축으로 수신자를
     *                  정한다. 이 계약은 회사 전체를 돌려주고 <b>거르는 일은 호출자가 한다</b> —
     *                  배치에는 {@code AccessContext}가 없어 여기서 판정할 수 없기 때문이다
     * @param companyId {@code findExpiringBetween}이 <b>전 회사</b>를 한 번에 돌려주므로 줄마다 필요하다 —
     *                  정지 회사 억제(Q-27) 판정과 메일·알림 발행이 회사 단위다
     */
    record QuoteSummary(UUID id, String quoteNo, UUID dealId, UUID companyId, String customerName,
                        Instant sentAt, Instant firstViewedAt, LocalDate validUntil) {}

    /**
     * 열람 페이지 렌더 데이터 — 금액 3분리는 항상 서버 계산값이다 (QT-08·22·25).
     *
     * @param dealId    D가 현재 담당자를 조회하는 축 (AP-18)
     * @param companyId D가 회사 정체성·정지 여부(SC-10)·알림 행 생성에 쓰는 축
     */
    record PublicQuoteView(UUID quoteId, String quoteNo, String status,
                           String vatMode, String terms, LocalDate validUntil,
                           Long supplyAmount, Long vatAmount, Long totalAmount,
                           List<Item> items,
                           UUID dealId, UUID companyId) {

        /**
         * 발송 시점 값 복사본 — product 조인이 아니다 (QT-24, PR-04 무영향).
         *
         * <p><b>{@code public}이 필요하다.</b> 인터페이스에 중첩된 {@code PublicQuoteView}는
         * 암묵적으로 public이지만, <b>record 안에 중첩된 이 타입은 그렇지 않다</b> —
         * 기본 접근이라 {@code com.twojo.boundary} 밖에서는 이름조차 쓸 수 없었다.
         * 항목 없는 뷰만 만들어 보면(빈 리스트) 드러나지 않는다.
         */
        public record Item(String name, String unit, int quantity,
                    Long unitPrice, Long amount, int sortOrder) {}
    }

    /**
     * 주문이 견적에서 물려받는 최소 정보 — 표시용 {@code quoteNo}와 범위 축인 {@code dealId}.
     *
     * <p>금액·항목은 여기 없다. 주문은 전환 시점 값을 <b>자기 테이블에 복사해 가지고</b>
     * 있어서(OD-04·05) 조회 때 견적을 다시 볼 이유가 없다 — 다시 보면 스냅샷이 무너진다.
     */
    record QuoteOrigin(UUID quoteId, String quoteNo, UUID dealId) {}
}
