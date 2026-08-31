package com.twojo.notification.repository;

import com.twojo.notification.entity.NotificationSetting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, UUID> {

    /** 발송 시 수신 설정 확인 (NT-07). 행이 없으면 기본 ON으로 취급 — 호출부 책임. */
    Optional<NotificationSetting> findByMemberIdAndType(UUID memberId, String type);
}
