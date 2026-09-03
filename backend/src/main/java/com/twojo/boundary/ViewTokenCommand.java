package com.twojo.boundary;

import java.util.UUID;

/**
 * 열람 링크 커맨드 계약 — 구현: D(approval 모듈). C가 호출한다 (경계 합의의 역방향).
 *
 * <p>발급은 C의 발송 트랜잭션 안에서 <b>동기 호출</b>된다 — 링크 없는 SENT 견적은 존재하지 않는다(Q-40).
 * 메일 발송만 커밋 후 비동기. (docs/11-work-breakdown.md §5 · §7.1)
 *
 * <p><b>C·D 합의 (2026-08-27)</b> — 아래 javadoc의 계약은 시그니처에 드러나지 않지만
 * 구현이 지켜야 하는 것이다. 문서에 없던 빈칸을 채운 결정이므로, 바꾸려면
 * 소유자(E) + 소비자(C·D) 합의가 다시 필요하다.
 */
public interface ViewTokenCommand {

    /**
     * 발송·재발송 시 발급 (Q-40 · AP-13).
     *
     * <p><b>C의 호출 순서</b>: 이 호출이 <b>성공한 뒤에</b> {@code quote.status = SENT}로 바꾼다.
     * 같은 트랜잭션이라 실패하면 순서와 무관하게 전부 롤백되므로, 순서가 지키는 것은 실패가 아니라
     * <b>성공 경로의 불변식</b>이다 — 어느 시점에도 "링크 없는 SENT"가 존재하지 않는다(Q-40).
     *
     * <p><b>메일은 C가 따로 부르지 않는다.</b> 토큰 생성과 안내 메일 예약({@code MailCommand.schedule})까지
     * 이 호출 안에서 끝난다. <b>메일 발송(AFTER_COMMIT 비동기) 실패는 C의 커밋을 되돌리지 않는다</b> —
     * email_log FAILED로 남는다(NT-12). 단, 토큰 생성과 메일 예약(schedule, 동기)의 실패는
     * 예외를 던져 C의 트랜잭션을 롤백시킨다.
     *
     * <p>재발송이면 기존 활성 링크를 {@link ExpiredReason#RESENT}로 만료시키고 새로 발급하는 것까지
     * <b>D 내부에서</b> 처리한다 — C는 {@code expire(RESENT)}를 부르지 않는다.
     */
    void issue(UUID quoteId, UUID recipientContactId);

    /**
     * 활성 링크 만료 (전이표 §7).
     *
     * <p><b>멱등하다.</b> 이미 만료됐거나 활성 링크가 없으면 예외 없이 아무 일도 하지 않는다(no-op).
     * 만료 배치(TIME) 직전에 Deal 실패(DEAL_LOST)가 겹치거나, 활성 링크 없는 견적을 회수하는 경우가
     * 실제로 있어 호출자가 분기를 두지 않아도 되게 한다.
     *
     * <p><b>대상은 ACTIVE 1건뿐</b>이다 — 견적당 활성 링크는 최대 1개(부분 유니크)이고,
     * 과거 이력 행은 그대로 둔다.
     *
     * <p>회사 정지 중에도 만료 전이는 계속 돈다 — <b>알림 억제 판정은 D가 한다</b> (Q-27).
     */
    void expire(UUID quoteId, ExpiredReason reason);

    /**
     * 만료 사유 — {@code quote_view_token.expired_reason}의 CHECK 값과 1:1이다.
     * <b>값을 바꾸면 마이그레이션도 함께 가야 한다.</b>
     *
     * <p>초대(invitation)에도 같은 이름의 컬럼이 있지만 값 집합이 다르므로(TIME·RESENT뿐)
     * 최상위가 아니라 이 인터페이스 안에 둔다. boundary의 다른 계약도 중첩을 쓴다
     * ({@code QuoteCommand.Responder} · {@code CustomerQuery.CustomerSummary}).
     *
     * <p><b>C가 넘기는 값이 넷</b>이다 — AP-13·14의 구성원용 엔드포인트도 C 소유이기 때문이다(11 §4).
     * D가 스스로 붙이는 값은 {@link #RESENT} 하나뿐이다.
     */
    enum ExpiredReason {

        /** 기간 경과 — 견적 만료 배치가 호출 (C 소유, Q-37) */
        TIME,

        /** 담당자가 링크를 직접 만료 — {@code POST /quotes/{id}/view-token/expire} (C 소유, AP-14) */
        MANUAL,

        /** 견적 회수 (QT-17) */
        WITHDRAWN,

        /**
         * 수신인 변경 재발송 (AP-13) — 기존 활성 링크를 닫고 새 링크를 연다.
         * <b>{@link #issue} 안에서 D가 붙이는 값</b>이다. C가 {@code expire(RESENT)}를 부르지 않는다.
         */
        RESENT,

        /** Deal 실패로 승인 경로가 닫힘 (DL-10 · 전이표 §5) */
        DEAL_LOST,
    }
}
