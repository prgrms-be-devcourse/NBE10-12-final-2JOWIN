package com.twojo.notification.service;

import com.twojo.boundary.NotificationSettingCommand;
import com.twojo.boundary.NotificationSettingQuery;
import com.twojo.boundary.NotificationSettingType;
import com.twojo.notification.entity.NotificationSetting;
import com.twojo.notification.repository.NotificationSettingRepository;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link NotificationSettingQuery}·{@link NotificationSettingCommand} 구현 (NT-07). A의
 * {@code /me/notification-settings} 엔드포인트가 이 계약으로 {@code notification_setting}을 읽고 쓴다.
 *
 * <p>{@code settingsOf}는 항상 {@link NotificationSettingType#values()} 전체를 채운다 — 저장 행이 없으면 ON,
 * {@code type} 문자열이 현재 enum에 없으면(폐기 상수) 그 행은 건너뛴다.
 *
 * <p>{@code replaceSettings}는 네이티브 upsert로 {@code (member_id, type)}를 갈아끼운다 — 같은 구성원의 동시
 * PUT에서도 {@code uk_notification_setting} 위반이 없다. 전파는 {@code REQUIRED}(독립 트랜잭션 —
 * {@code MANDATORY}로 호출 구조를 강제하지 않는다. NT-07엔 상위 비즈니스 액션이 없다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class NotificationSettingService implements NotificationSettingQuery, NotificationSettingCommand {

    private final NotificationSettingRepository repository;

    @Override
    public Map<NotificationSettingType, Boolean> settingsOf(UUID memberId) {
        Map<NotificationSettingType, Boolean> stored = new EnumMap<>(NotificationSettingType.class);
        for (NotificationSetting row : repository.findByMemberId(memberId)) {
            NotificationSettingType type;
            try {
                type = NotificationSettingType.valueOf(row.getType());
            } catch (IllegalArgumentException unknown) {
                continue;   // 폐기된 설정 대상의 잔여 행 — 무시
            }
            stored.put(type, row.isEnabled());
        }
        Map<NotificationSettingType, Boolean> result = new EnumMap<>(NotificationSettingType.class);
        for (NotificationSettingType type : NotificationSettingType.values()) {
            result.put(type, stored.getOrDefault(type, true));
        }
        return result;
    }

    @Override
    @Transactional
    public void replaceSettings(UUID memberId, Map<NotificationSettingType, Boolean> settings) {
        for (NotificationSettingType type : NotificationSettingType.values()) {
            if (settings.get(type) == null) {
                throw new IllegalArgumentException("설정에 " + type + "가 없거나 값이 null이다");
            }
        }
        for (NotificationSettingType type : NotificationSettingType.values()) {
            repository.upsert(UUID.randomUUID(), memberId, type.name(), settings.get(type));
        }
    }
}
