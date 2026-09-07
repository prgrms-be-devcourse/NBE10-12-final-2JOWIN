package com.twojo.notification.service;

import com.twojo.boundary.AccessContext;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.notification.dto.NotificationResponse;
import com.twojo.notification.entity.Notification;
import com.twojo.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인앱 알림 조회·읽음 (07 §D · NT-08).
 *
 * <p>조회 키가 요청이 아니라 {@link AccessContext}에서 온다 — 남의 알림을 지정할 자리가 없다.
 * {@code company_id}까지 스코프에 넣는다(SC-01 방어적 이중). "없음"·"내 것 아님"·"다른 회사"는
 * 전부 {@code RESOURCE_NOT_FOUND}로 통일한다(SC-09).
 *
 * <p>읽음 개수는 별도 엔드포인트가 없다 — 프론트가 {@code unreadOnly=true} 응답의
 * {@code totalElements}로 배지를 그린다(docs/10-screen-design.md §6.4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public PageResponse<NotificationResponse> list(AccessContext ctx, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByCompanyIdAndRecipientMemberIdAndReadAtIsNull(
                        ctx.companyId(), ctx.memberId(), pageable)
                : notificationRepository.findByCompanyIdAndRecipientMemberId(
                        ctx.companyId(), ctx.memberId(), pageable);
        return PageResponse.from(page.map(NotificationService::toResponse));
    }

    @Transactional
    public void markRead(AccessContext ctx, UUID id) {
        Notification notification = notificationRepository
                .findByIdAndCompanyIdAndRecipientMemberId(id, ctx.companyId(), ctx.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        notification.markRead();   // 이미 읽음이면 엔티티가 무동작 (멱등)
    }

    @Transactional
    public void markAllRead(AccessContext ctx) {
        List<Notification> unread = notificationRepository
                .findByCompanyIdAndRecipientMemberIdAndReadAtIsNull(ctx.companyId(), ctx.memberId());
        unread.forEach(Notification::markRead);   // dirty checking으로 flush
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType().name(), n.getMessage(),
                n.getRefType(), n.getRefId(), n.getReadAt(), n.getCreatedAt());
    }
}
