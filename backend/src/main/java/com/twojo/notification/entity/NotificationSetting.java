package com.twojo.notification.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구성원별 메일 수신 설정 (NT-07) — 메일 채널에만 적용 (Q-23). 행 없으면 기본 ON.
 * 설정 API는 A 소유, 발송 시 확인은 D 책임.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID memberId;

    private String type;

    private boolean enabled;

    /** 메일 채널 수신 여부 변경 (NT-07). 설정 API는 A 소유, 이 메서드는 그 반영용. */
    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
