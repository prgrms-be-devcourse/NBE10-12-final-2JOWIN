package com.twojo.boundary;

import java.util.UUID;

/**
 * 고객이 보는 견적 화면 응답 조립 계약 — 구현: D(approval 모듈).
 * approval(고객 열람 {@code GET /public/api/v1/quotes/{token}})과
 * quote(구성원 미리보기 {@code GET /quotes/{id}/preview}, QT-12)가 호출한다.
 *
 * <p><b>왜 계약 하나로 모으나</b> — 08 §D(:323)가 "preview는 D의 {@link PublicQuoteResponse}를
 * 그대로 재사용 — 고객이 볼 화면과 동일 보장"으로 못박았다. 조립을
 * ({@link QuoteQuery#getPublicView} + {@link CompanyQuery} + {@link DealQuery#assigneeIdOf}
 * + {@link MemberQuery}) 양쪽이 각자 하면 동일 로직이 두 벌이 되어 respondable 공식·담당자
 * 해석·필드 추가 시 한쪽만 바뀐다. 통로를 여기 하나로 모은다 — {@link NotificationCommand}와 동형이다.
 *
 * <p><b>이 계약이 하는 일</b> — 순수 read + shape다.
 * <ul>
 *   <li>{@code getPublicView}(quote 소유 데이터) + 회사 정체성({@code CompanyQuery.get}) +
 *       Deal의 <b>현재</b> 담당자({@code DealQuery.assigneeIdOf} → {@code MemberQuery.getContact},
 *       AP-18 — 발송자 스냅샷이 아니다)를 하나로 조립한다.</li>
 *   <li>항목은 {@code sortOrder} 오름차순으로 정렬한다.</li>
 *   <li>내부 식별자({@code dealId}·{@code companyId})는 응답에 싣지 않는다 — 고객 화면용 모양이다.</li>
 *   <li>견적이 없으면 {@code getPublicView}가 {@code BusinessException(RESOURCE_NOT_FOUND)}를
 *       던지고 그대로 전파된다.</li>
 * </ul>
 *
 * <p><b>이 계약이 하지 않는 일 — 호출자가 책임진다</b>:
 * <ul>
 *   <li><b>구성원 스코프 판정</b> — 남의 담당 딜 견적 404(SC-02·09)는 미리보기 호출자(C)가
 *       조립기 호출 전에 막는다. 조립기는 {@code quoteId}만 받고 권한 맥락을 모른다.</li>
 *   <li><b>토큰 검증</b> — 해시 조회 404 / 만료 410 / RESPONDED 재응답 409는 고객 열람 호출자(D)가
 *       처리한다.</li>
 *   <li><b>DRAFT·WITHDRAWN 차단</b> — 고객 열람 경로의 404는 D 호출자가 조립기 호출 전에 건다.
 *       {@link #assembleForPreview}는 반대로 <b>DRAFT를 정상 조립</b>한다 (발송 전 미리보기가 목적).</li>
 *   <li><b>첫 열람 부수효과</b>({@code markViewed} + NT-03) — D 호출자가 {@link #assembleForView}
 *       호출 전에 끝낸다. 조립기는 현재 저장된 {@code status}를 다시 읽어 응답에 담는다 —
 *       첫 열람 전이가 이미 커밋됐으므로 대개 {@code VIEWED}다.</li>
 * </ul>
 *
 * <p>조립기는 무트랜잭션이다 — 각 boundary 구현이 자체 readOnly 트랜잭션을 잡는다.
 * 시그니처 변경은 소유자(D) + 소비자(C) 합의로만 한다.
 */
public interface PublicQuoteAssembler {

    /**
     * 발송 전 미리보기용 (QT-12). {@code respondable = false} 고정 — 아직 링크가 없다.
     * DRAFT 견적도 예외 없이 조립한다.
     */
    PublicQuoteResponse assembleForPreview(UUID quoteId);

    /**
     * 고객 열람용 (AP-02·07). {@code respondable = 회사 active && linkRespondable &&
     * status ∈ {SENT, VIEWED}}.
     *
     * @param linkRespondable 호출자가 접어 넘기는 링크 상태 — {@code token.isRespondable(now)}
     *                        (ACTIVE 이고 만료 전). 조립기는 시간을 다루지 않는다.
     */
    PublicQuoteResponse assembleForView(UUID quoteId, boolean linkRespondable);
}
