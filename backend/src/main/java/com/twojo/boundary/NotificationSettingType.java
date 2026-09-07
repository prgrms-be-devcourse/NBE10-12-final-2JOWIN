package com.twojo.boundary;

/**
 * 구성원 메일 수신 설정의 대상 (NT-07 · docs/03-requirements.md §2.13). 인앱 알림은 항상 오고,
 * 이 4종의 병행 메일만 끌 수 있다.
 *
 * <p>{@link NotificationCommand.NotificationType}과 1:1이 아니다 — {@code EMAIL_FAILED}는 인앱 전용이라
 * (Q-35) 설정 대상이 아니고, {@code QUOTE_APPROVED}·{@code QUOTE_REJECTED}는 NT-04 "승인·반려 결과"
 * 하나로 묶여 {@link #QUOTE_RESPONDED} 한 토글이다.
 *
 * <p>값 추가·삭제는 "이게 설정 대상인가"를 매번 명시적으로 결정한다 — {@code NotificationType}에서
 * 역산하지 않는다. 상수를 늘려도 마이그레이션은 없다({@code settingsOf}가 없는 행을 ON으로 채운다).
 */
public enum NotificationSettingType {

    /** NT-03 — 고객이 견적을 열람했을 때 담당 구성원에게. */
    QUOTE_VIEWED,

    /** NT-04 — 고객이 승인·반려했을 때 담당 구성원에게 (승인·반려 한 토글). */
    QUOTE_RESPONDED,

    /** NT-05 — 무응답 견적 리마인드 배치가 담당 구성원에게. */
    REMIND_NO_RESPONSE,

    /** NT-10 — 고객이 문의를 남겼을 때 담당 구성원·기업 관리자에게. */
    INQUIRY_RECEIVED
}
