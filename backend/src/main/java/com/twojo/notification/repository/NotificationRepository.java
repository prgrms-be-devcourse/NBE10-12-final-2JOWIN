package com.twojo.notification.repository;

import com.twojo.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** 본인 수신 알림 목록 (GET /api/v1/notifications) — company_id까지 스코프(SC-01 방어적 이중). */
    Page<Notification> findByCompanyIdAndRecipientMemberId(
            UUID companyId, UUID recipientMemberId, Pageable pageable);

    /** 미읽음만 (?unreadOnly=true). totalElements가 곧 배지 수. */
    Page<Notification> findByCompanyIdAndRecipientMemberIdAndReadAtIsNull(
            UUID companyId, UUID recipientMemberId, Pageable pageable);

    /** 모두 읽음 처리용 — 서비스가 순회하며 markRead()를 호출한다 (read-all). */
    List<Notification> findByCompanyIdAndRecipientMemberIdAndReadAtIsNull(
            UUID companyId, UUID recipientMemberId);

    /**
     * 읽음 처리 대상 1건 — id + 회사 + 본인 3-way. 없거나 남의 것이거나 다른 회사면 비어 있고,
     * 서비스가 이를 RESOURCE_NOT_FOUND로 바꾼다 (SC-09 존재 비노출).
     */
    Optional<Notification> findByIdAndCompanyIdAndRecipientMemberId(
            UUID id, UUID companyId, UUID recipientMemberId);

    /**
     * NT-05 리마인드 배치의 멱등 가드 — 이 견적에 이미 리마인드 알림이 있으면 배치가 견적 전체를 건너뛴다
     * (인앱·메일 두 레그 함께). 수신자를 가리지 않는다 — 담당자가 재배정돼도 재알림하지 않는다
     * (견적당 1회, docs/11-work-breakdown.md §5).
     */
    boolean existsByCompanyIdAndTypeAndRefId(UUID companyId, Notification.Type type, UUID refId);
}
