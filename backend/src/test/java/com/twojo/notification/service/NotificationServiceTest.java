package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.notification.dto.NotificationResponse;
import com.twojo.notification.entity.Notification;
import com.twojo.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link NotificationService} — 조회 시 unreadOnly 분기·회사/본인 스코프·DTO 매핑, markRead의 3-way 404와
 * 멱등, markAllRead. 실제 UPDATE가 flush되는지는 통합 테스트가 검증한다(목 리포지토리로는 못 잡음).
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationServiceTest {

    private static final UUID COMPANY = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID MEMBER = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final AccessContext CTX =
            new AccessContext(COMPANY, MEMBER, Role.SALES_REP, AccessScope.OWNED_ONLY);
    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Mock
    private NotificationRepository notificationRepository;
    @InjectMocks
    private NotificationService service;

    private static Notification notification(UUID id, Instant readAt) {
        Notification n = Notification.of(COMPANY, MEMBER, Notification.Type.QUOTE_VIEWED,
                "고객이 견적을 열람했습니다", "QUOTE", UUID.randomUUID());
        ReflectionTestUtils.setField(n, "id", id);
        ReflectionTestUtils.setField(n, "createdAt", Instant.now());
        if (readAt != null) {
            ReflectionTestUtils.setField(n, "readAt", readAt);
        }
        return n;
    }

    @Test
    @DisplayName("list - unreadOnly=false면 전체를 회사+본인 스코프로 조회해 NotificationResponse로 매핑한다")
    void list_전체_조회_매핑() {
        given(notificationRepository.findByCompanyIdAndRecipientMemberId(COMPANY, MEMBER, PAGEABLE))
                .willReturn(new PageImpl<>(List.of(notification(UUID.randomUUID(), null)), PAGEABLE, 1));

        PageResponse<NotificationResponse> res = service.list(CTX, false, PAGEABLE);

        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).type()).isEqualTo("QUOTE_VIEWED");
        assertThat(res.content().get(0).refType()).isEqualTo("QUOTE");
        verify(notificationRepository, never())
                .findByCompanyIdAndRecipientMemberIdAndReadAtIsNull(any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("list - unreadOnly=true면 미읽음 쿼리를 쓴다")
    void list_미읽음만() {
        given(notificationRepository.findByCompanyIdAndRecipientMemberIdAndReadAtIsNull(COMPANY, MEMBER, PAGEABLE))
                .willReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

        service.list(CTX, true, PAGEABLE);

        verify(notificationRepository)
                .findByCompanyIdAndRecipientMemberIdAndReadAtIsNull(COMPANY, MEMBER, PAGEABLE);
    }

    @Test
    @DisplayName("markRead - 본인·회사 알림이면 읽음 처리한다")
    void markRead_정상() {
        UUID id = UUID.randomUUID();
        Notification n = notification(id, null);
        given(notificationRepository.findByIdAndCompanyIdAndRecipientMemberId(id, COMPANY, MEMBER))
                .willReturn(Optional.of(n));

        service.markRead(CTX, id);

        assertThat(n.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("markRead - 없거나 남의 것이거나 다른 회사면 RESOURCE_NOT_FOUND (3-way, SC-09)")
    void markRead_대상_없으면_404() {
        UUID id = UUID.randomUUID();
        given(notificationRepository.findByIdAndCompanyIdAndRecipientMemberId(id, COMPANY, MEMBER))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(CTX, id))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("markRead - 이미 읽은 알림이면 시각을 바꾸지 않는다 (멱등)")
    void markRead_이미_읽음이면_멱등() {
        UUID id = UUID.randomUUID();
        Instant firstRead = Instant.parse("2026-09-01T00:00:00Z");
        Notification n = notification(id, firstRead);
        given(notificationRepository.findByIdAndCompanyIdAndRecipientMemberId(id, COMPANY, MEMBER))
                .willReturn(Optional.of(n));

        service.markRead(CTX, id);

        assertThat(n.getReadAt()).isEqualTo(firstRead);
    }

    @Test
    @DisplayName("markAllRead - 본인 미읽음 전체를 읽음 처리한다")
    void markAllRead_전체() {
        Notification a = notification(UUID.randomUUID(), null);
        Notification b = notification(UUID.randomUUID(), null);
        given(notificationRepository.findByCompanyIdAndRecipientMemberIdAndReadAtIsNull(COMPANY, MEMBER))
                .willReturn(List.of(a, b));

        service.markAllRead(CTX);

        assertThat(a.getReadAt()).isNotNull();
        assertThat(b.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("markAllRead - 미읽음이 없으면 아무 일도 하지 않는다")
    void markAllRead_없으면_무동작() {
        given(notificationRepository.findByCompanyIdAndRecipientMemberIdAndReadAtIsNull(COMPANY, MEMBER))
                .willReturn(List.of());

        assertThatCode(() -> service.markAllRead(CTX)).doesNotThrowAnyException();
    }
}
