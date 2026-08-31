package com.twojo.notification.repository;

import com.twojo.notification.entity.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** 본인 수신 알림 목록 (GET /api/v1/notifications). */
    Page<Notification> findByRecipientMemberId(UUID recipientMemberId, Pageable pageable);

    /** 미읽음만 (?unreadOnly=true). */
    Page<Notification> findByRecipientMemberIdAndReadAtIsNull(UUID recipientMemberId, Pageable pageable);

    /** 모두 읽음 처리용 — 서비스가 순회하며 markRead()를 호출한다 (read-all). */
    List<Notification> findByRecipientMemberIdAndReadAtIsNull(UUID recipientMemberId);
}
