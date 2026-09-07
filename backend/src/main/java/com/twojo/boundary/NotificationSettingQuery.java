package com.twojo.boundary;

import java.util.Map;
import java.util.UUID;

/**
 * 구성원 메일 수신 설정 조회 계약 — 구현: D(notification 모듈). A의 {@code GET /api/v1/me/notification-settings}가
 * 소비한다 (NT-07). {@code notification_setting} 테이블이 notification 모듈 내부라 이 통로로 읽는다.
 *
 * <p>쓰기는 {@link NotificationSettingCommand}. Query/Command를 나눈 이유는 다른 경계 계약과 같다 —
 * 조회는 {@code readOnly} 트랜잭션이다.
 */
public interface NotificationSettingQuery {

    /**
     * {@code memberId}의 메일 수신 설정 — <b>항상 {@link NotificationSettingType#values()} 전체를 채워</b>
     * 돌려준다. 저장된 행이 없는 type은 {@code true}(기본 ON, docs/11-work-breakdown.md §5).
     *
     * <p>{@code notification_setting.type}에 현재 enum에 없는 값이 있으면(설정 대상에서 뺀 폐기 상수의
     * 잔여 행) 그 행은 건너뛴다 — 나머지는 정상 반환한다.
     *
     * <p>{@code memberId}는 {@code /me} 인증 principal에서 오므로 항상 유효하다 — 존재 여부를 방어하지 않는다.
     *
     * <p><b>주의</b> — 같은 트랜잭션에서 {@code settingsOf} → {@link NotificationSettingCommand#replaceSettings}
     * → {@code settingsOf} 순으로 부르면, {@code replaceSettings}의 네이티브 upsert가 1차 캐시(영속성 컨텍스트)를
     * 갱신하지 않아 두 번째 조회가 과거 값을 반환할 수 있다. GET·PUT은 별도 요청이라 정상 흐름엔 없다 —
     * 리뷰에서 프로젝션 대신 이 주석으로 갈음.
     *
     * @return 4개 항목이 모두 있는 맵 (type → 수신 여부). 호출자는 {@code get}이 null이 아님을 전제해도 된다
     */
    Map<NotificationSettingType, Boolean> settingsOf(UUID memberId);
}
