package com.twojo.notification.repository;

import com.twojo.notification.entity.NotificationSetting;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, UUID> {

    /** 발송 시 수신 설정 확인 (NT-07). 행이 없으면 기본 ON으로 취급 — 호출부 책임. */
    Optional<NotificationSetting> findByMemberIdAndType(UUID memberId, String type);

    /** 구성원의 설정 전체 (NT-07 조회). {@code NotificationSettingQuery.settingsOf}가 없는 type을 ON으로 채운다. */
    List<NotificationSetting> findByMemberId(UUID memberId);

    /**
     * 전체 교체 upsert (NT-07). {@code (member_id, type)}가 있으면 {@code enabled}만 갱신 —
     * 같은 구성원의 동시 PUT에서도 {@code uk_notification_setting} 위반이 없다.
     *
     * <p>{@code id}는 호출자가 {@code UUID.randomUUID()}로 넘긴다(코드베이스가 UUID를 Java에서 생성).
     * ON CONFLICT 경로에선 그 값이 버려진다. {@code created_at}/{@code updated_at}은 DDL 기본값에 기대지
     * 않고 명시한다(네이티브라 JPA Auditing이 안 돈다). {@code @Modifying}에 {@code flush}/{@code clear}를
     * 붙이지 않는다 — PC와 동기화할 엔티티가 없고, {@code clearAutomatically}는 호출자의 영속성 컨텍스트까지 비운다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification_setting (id, member_id, type, enabled, created_at, updated_at)
            VALUES (:id, :memberId, :type, :enabled, now(), now())
            ON CONFLICT (member_id, type) DO UPDATE SET enabled = excluded.enabled, updated_at = now()
            """, nativeQuery = true)
    int upsert(@Param("id") UUID id, @Param("memberId") UUID memberId,
               @Param("type") String type, @Param("enabled") boolean enabled);
}
