package com.twojo.boundary;

import java.util.UUID;

/**
 * 인앱 알림 생성 계약 — 구현: D(notification 모듈).
 * approval(고객 열람·승인·문의)·notification 내부(리마인드 배치·메일 디스패처)가 호출한다.
 *
 * <p><b>왜 계약 하나로 모으나</b> — {@code notification}은 notification 모듈 소유인데, 알림을 만드는
 * 흐름은 approval(NT-03 열람·NT-08 승인반려·NT-10 문의)에도 있다. 모듈이 서로의 내부(entity·repository)를
 * 직접 참조하지 않도록 쓰기 통로를 여기 하나로 모은다 — {@code MailCommand}와 동형이다.
 *
 * <p><b>호출 규약</b> (시그니처에 안 드러나지만 구현이 지킨다):
 * <ul>
 *   <li><b>호출자 트랜잭션에 합류</b>한다({@code MANDATORY}) — 비즈니스 액션(승인·문의 등)이 롤백되면
 *       그에 딸린 알림도 함께 사라져야 한다. 트랜잭션 밖 호출은 {@code IllegalTransactionStateException}.</li>
 *   <li>{@code message}는 <b>렌더 완료본</b>이다 — 호출자가 완성한 한글 문자열. 구현은 500자를 넘으면
 *       잘라 저장한다({@code notification.message VARCHAR(500)}).</li>
 *   <li>수신자별로 행 1건이다. {@link #notifyForDeal}은 타입별 수신자 규칙(Q-26 폴백 / NT-10 union)을
 *       구현이 해석하고, {@link #notify}는 호출자가 이미 정한 수신자 1명에게만 쓴다.</li>
 * </ul>
 *
 * <p><b>{@code RefType}을 {@link #notify} 파라미터로 둔다</b> — {@code MailCommand.TemplateType}이
 * {@code refType()}를 상수로 고정한 것과 반대다. 이유: {@code EMAIL_FAILED}의 {@code refId}가 가리키는
 * 대상이 실패한 메일 종류마다 달라(견적 메일이면 견적, 초대 메일이면 초대, 승인·재설정 메일이면 없음 —
 * docs/03-requirements.md §2.13) 타입 하나에 refType 하나로 접히지 않는다. 견적 컨텍스트 5종은
 * {@link #notifyForDeal}이 {@code RefType.QUOTE}를 자동으로 채워 호출자가 불일치 쌍을 만들 여지를 없앤다.
 *
 * <p>시그니처·enum 상수의 <b>이름 변경·삭제</b>는 소유자(E) + 소비자 합의가 필요하다.
 * enum <b>값 추가는 D 단독</b>으로 한다 — D가 notification 소유이고 기존 소비자에 영향이 없다.
 */
public interface NotificationCommand {

    /**
     * 알림 1건 — 이미 정해진 수신자에게. 호출자 트랜잭션에 합류({@code MANDATORY}).
     *
     * <p>{@code EMAIL_FAILED}(메일 디스패처)처럼 수신자를 호출자가 직접 정하는 경우에 쓴다.
     * 견적 알림은 {@link #notifyForDeal}을 쓴다.
     *
     * <p>{@code refType}과 {@code refId}는 <b>함께 있거나 함께 없어야 한다</b> — 한쪽만 있으면 프론트가
     * 죽은 링크를 그린다. 구현이 강제한다(위반 시 {@code IllegalArgumentException}). 둘 다 null이면
     * 클릭 이동이 없는 알림이다.
     */
    void notify(NotificationType type, UUID companyId, UUID recipientMemberId,
                String message, RefType refType, UUID refId);

    /**
     * Deal 담당자(들)에게 알림 — 타입별 수신자 규칙은 구현이 해석한다(docs/03-requirements.md §2.13 표).
     * <ul>
     *   <li>{@code QUOTE_VIEWED}·{@code QUOTE_APPROVED}·{@code QUOTE_REJECTED}·{@code REMIND_NO_RESPONSE}
     *       — 현재 담당자. 담당자가 비활성이면 기업 관리자 전원(Q-26).</li>
     *   <li>{@code INQUIRY_RECEIVED} — 담당자(활성 시) <b>및</b> 기업 관리자 전원(NT-10).</li>
     * </ul>
     * refType은 {@code RefType.QUOTE}로 고정, {@code refId}는 {@code quoteId}다 — {@code quoteId}가
     * null이면 {@code IllegalArgumentException}(refType/refId 짝 불변식).
     * {@code EMAIL_FAILED}는 Deal 컨텍스트가 아니므로 이 메서드로 부르면 {@code IllegalArgumentException}.
     *
     * <p>{@code companyId}와 {@code dealId}는 <b>같은 조회에서 얻은 쌍</b>이어야 한다 —
     * {@code DealQuery.assigneeIdOf}는 회사 스코프를 걸지 않으므로, 어긋난 쌍이면 담당자가 다른 회사로 풀려
     * {@code notification} 복합 FK 위반이 flush 시점에 호출자 트랜잭션을 롤백시킨다.
     */
    void notifyForDeal(NotificationType type, UUID companyId, UUID dealId,
                       String message, UUID quoteId);

    /**
     * 알림 종류 — {@code notification.type} CHECK 값(docs/06-erd.md)과 1:1. 엔티티 {@code Notification.Type}과
     * 이름이 같아 구현이 {@code switch}로 브리지한다(드리프트를 빌드에서 잡는다).
     */
    enum NotificationType {

        /** NT-03 고객이 견적을 열람 */
        QUOTE_VIEWED,

        /** NT-08 고객이 견적을 승인 */
        QUOTE_APPROVED,

        /** NT-08 고객이 견적을 반려 */
        QUOTE_REJECTED,

        /** NT-05 응답 없는 견적 리마인드 (배치) */
        REMIND_NO_RESPONSE,

        /** NT-10 고객이 문의를 남김 */
        INQUIRY_RECEIVED,

        /** NT-12 시스템 메일 발송 실패 (재시도 후) — 인앱 전용, Q-35 */
        EMAIL_FAILED
    }

    /**
     * 클릭 이동 대상의 종류 — {@code notification.ref_type}에 {@code name()}이 저장된다.
     * null이면 이동 없음. {@code refId}와 항상 짝이다.
     *
     * <p>현재 모든 알림의 이동 대상이 견적 상세(docs/10-screen-design.md §6.4)라 값이 하나다.
     * {@code email_log.ref_type}의 {@code "QUOTE_VIEW_TOKEN"}과 대문자 관례를 맞춘다.
     */
    enum RefType { QUOTE }
}
