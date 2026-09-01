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

    /** firstViewedAt이 null이면 미열람 (v2.0.2, GAP-08) */
    record QuoteSummary(UUID id, String quoteNo, String customerName,
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

        /** 발송 시점 값 복사본 — product 조인이 아니다 (QT-24, PR-04 무영향) */
        record Item(String name, String unit, int quantity,
                    Long unitPrice, Long amount, int sortOrder) {}
    }
}
