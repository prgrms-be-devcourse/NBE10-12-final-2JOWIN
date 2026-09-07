package com.twojo.boundary;

import java.util.Map;
import java.util.UUID;

/**
 * 구성원 메일 수신 설정 쓰기 계약 — 구현: D(notification 모듈). A의 {@code PUT /api/v1/me/notification-settings}가
 * 소비한다 (NT-07). 읽기는 {@link NotificationSettingQuery}.
 */
public interface NotificationSettingCommand {

    /**
     * 전체 교체 (PUT 의미) — {@code settings}는 {@link NotificationSettingType} 4종을 <b>전부 non-null</b>로
     * 담아야 한다. 하나라도 빠지거나 값이 null이면 {@code IllegalArgumentException}이다 — 이건 백스톱이고,
     * 완전성·중복 검증은 호출자(A의 엔드포인트)가 앞단에서 {@code VALIDATION_FAILED}(400)로 한다.
     *
     * <p><b>호출자 트랜잭션에 합류</b>하며, 없으면 새로 연다({@code REQUIRED} — {@code MANDATORY} 아님).
     * 알림 설정 변경은 독립 비즈니스 트랜잭션이라 호출 구조를 강제하지 않는다.
     *
     * <p>{@code notification_setting}을 {@code (member_id, type)}로 upsert한다 — 같은 구성원의 동시 PUT에서도
     * {@code uk_notification_setting} 위반이 없다. PUT이 항상 4종을 다 보내므로 폐기 행이 남지 않는다.
     */
    void replaceSettings(UUID memberId, Map<NotificationSettingType, Boolean> settings);
}
