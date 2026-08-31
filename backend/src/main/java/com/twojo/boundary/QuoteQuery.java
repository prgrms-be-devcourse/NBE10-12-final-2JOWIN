package com.twojo.boundary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 견적 후보 조회 계약 — 구현: C(quote 모듈). D의 배치·대시보드가 소비한다 (v2.0.1 보강).
 * (docs/11-work-breakdown.md §4)
 */
public interface QuoteQuery {

    /** SENT·VIEWED — NT-05 리마인드 · DB-03 응답 대기 */
    List<QuoteSummary> findAwaitingResponse(UUID companyId);

    /** NT-06 임박 알림 후보 (valid_until 기준) */
    List<QuoteSummary> findExpiringUntil(LocalDate date);

    /**
     * 고객 열람 페이지의 견적 본문 (AP-03 · 07-api-spec.md §D).
     *
     * <p><b>유효한 고객 열람 토큰을 검증한 뒤, 그 토큰 행에서 얻은 {@code quoteId}로만 호출한다.
     * 외부 요청이나 사용자 입력으로 전달된 {@code quoteId}를 직접 사용하면 안 된다.
     * 구성원용 preview는 {@link AccessContext}를 받는 별도 조회 경로를 사용한다.</b>
     *
     * <p><b>이 메서드에는 스코프가 없다.</b> {@code companyId}도 {@code AccessContext}도 받지 않으므로
     * {@code quoteId}만 알면 어느 회사 견적이든 읽힌다 — 그것이 SC-01 테넌트 격리를 우회하는
     * 유일한 구멍이다. 그래도 무스코프인 이유는 고객 열람이 <b>로그인 없는 경로</b>라서다:
     * 고객에게는 회사도 구성원도 없고, 링크 토큰 자체가 인증이다(SC-07~09).
     * 안전은 <b>호출 순서가 지킨다</b>:
     *
     * <ol>
     *   <li>요청의 원문 토큰을 해시해 {@code quote_view_token}을 조회</li>
     *   <li>토큰 상태·만료 검증 (만료 410 / 응답 완료 409 / 형식 오류도 404, SC-07~09)</li>
     *   <li>검증을 통과한 <b>토큰 행에서</b> {@code quoteId}를 꺼낸다</li>
     *   <li>그 {@code quoteId}로만 이 메서드를 호출</li>
     * </ol>
     *
     * <p>즉 <b>토큰 판정은 여기서 하지 않는다</b> — D의 책임이고(§5), 여기는 판정을 통과한 뒤
     * 그릴 데이터만 준다. 견적 존재 여부로 404를 가르는 것도 D가 한다.
     *
     * <p><b>C의 인증된 {@code /quotes/{id}/preview}에서 이 메서드를 그대로 쓰지 않는다.</b>
     * 거기서는 {@code id}가 사용자 입력이라 회사 스코프 검사가 반드시 필요하다 —
     * {@code AccessContext}를 받는 C 내부 조회를 따로 둔다.
     *
     * <p><b>회사 정보는 넣지 않는다.</b> {@code companyName}·사업자번호·정지 여부는 A 소유다.
     * D가 여기서 받은 {@code companyId}로 {@code CompanyQuery.get()}을 불러 조립한다 —
     * 견적 데이터와 회사 데이터의 소유자가 다르므로 통로도 나눈다(§7.2).
     *
     * <p>담당자 정보({@code PublicQuoteResponse.assignee}, AP-18)도 여기 없다.
     * "Deal의 <b>현재</b> 담당자 동적 조회"라 스냅샷이면 안 되고, D가
     * {@code DealQuery.assigneeIdOf(dealId)} → {@code MemberQuery.get()}으로 그때그때 읽는다.
     */
    PublicQuoteView getPublicView(UUID quoteId);

    /** firstViewedAt이 null이면 미열람 (v2.0.2, GAP-08) */
    record QuoteSummary(UUID id, String quoteNo, String customerName,
                        Instant sentAt, Instant firstViewedAt, LocalDate validUntil) {}

    /**
     * 고객 열람 페이지에 실리는 <b>C 소유 데이터만</b>.
     *
     * <p>타입은 {@code quote}·{@code quote_item} 엔티티 그대로다 — 금액은 원 단위 정수
     * {@code Long}(QT-08·22 서버 계산값), 유효기간은 {@code LocalDate}(Q-17).
     * 새 금액 타입을 만들지 않는다.
     *
     * @param status   전이표 §6의 영문 코드 문자열 (DRAFT/SENT/VIEWED/…) — boundary는
     *                 {@code quote.entity}를 참조하지 않으므로 {@code DealQuery.DealSummary.stage}와
     *                 같은 방식으로 문자열로 넘긴다
     * @param vatMode  {@code EXCLUDED} / {@code INCLUDED} (Q-16)
     * @param items    <b>{@code sortOrder} 오름차순으로 정렬해 반환한다</b>(QT-07) — 받는 쪽이
     *                 다시 정렬하지 않는다. 그래서 {@link Item}에 {@code sortOrder}를 노출하지
     *                 않는다: 순서를 값으로 내보내면 "정렬이 누구 책임인가"가 다시 열리고,
     *                 소비자가 그 값을 무시하거나 재정렬하는 두 갈래가 생긴다
     * @param dealId   AP-18 담당자 조회 · AC-06 타임라인 병합 키
     * @param companyId D가 {@code CompanyQuery.get()}으로 회사명·사업자번호·정지 여부를 조립할 열쇠
     */
    record PublicQuoteView(String quoteNo, String status, String vatMode, String terms,
                           LocalDate validUntil,
                           Long supplyAmount, Long vatAmount, Long totalAmount,
                           List<Item> items,
                           UUID dealId, UUID companyId) {

        /** 작성 시점 값 복사본 (QT-24) — product를 조인해 표시하지 않는다. */
        public record Item(String name, String unit, int quantity, Long unitPrice, Long amount) {}
    }
}
