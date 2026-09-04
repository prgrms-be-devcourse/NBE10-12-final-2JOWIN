package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.twojo.boundary.DealQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.NotificationCommand.NotificationType;
import com.twojo.boundary.NotificationCommand.RefType;
import com.twojo.notification.entity.Notification;
import com.twojo.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link NotificationCommandImpl} — 수신자 해석(Q-26 폴백 / NT-10 union / 중복 제거)과 message 절삭,
 * refType/refId 짝 불변식, 계약 enum → 엔티티 enum 브리지를 검증한다.
 * {@code @Transactional(MANDATORY)}의 "트랜잭션 밖 호출 거부"와 실 PG 저장(절삭·ref_type·복합 FK)은
 * 프록시·DB가 필요해 {@link NotificationPersistenceIntegrationTest}가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationCommandImplTest {

    private static final UUID COMPANY = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID DEAL = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID QUOTE = UUID.fromString("6b000000-0000-4000-8000-000000000001");
    private static final UUID ASSIGNEE = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID RECIPIENT = UUID.fromString("a0000000-0000-4000-8000-000000000002");
    private static final UUID ADMIN_1 = UUID.fromString("a0000000-0000-4000-8000-000000000009");
    private static final UUID ADMIN_2 = UUID.fromString("a0000000-0000-4000-8000-00000000000a");

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DealQuery dealQuery;
    @Mock
    private MemberQuery memberQuery;
    @InjectMocks
    private NotificationCommandImpl command;

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private List<Notification> savedAll(int n) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(n)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("notify는 호출자가 넘긴 RefType과 refId를 그대로 저장한다 (null이면 이동 없는 알림)")
    void notify_refType_refId_그대로_저장() {
        command.notify(NotificationType.EMAIL_FAILED, COMPANY, RECIPIENT,
                "메일 발송에 실패했습니다", null, null);

        Notification n = saved();
        assertThat(n.getType()).isEqualTo(Notification.Type.EMAIL_FAILED);
        assertThat(n.getRecipientMemberId()).isEqualTo(RECIPIENT);
        assertThat(n.getRefType()).isNull();
        assertThat(n.getRefId()).isNull();
    }

    @Test
    @DisplayName("notify - refType은 있는데 refId가 null이면 예외 (짝 불변식)")
    void notify_refType만_있으면_예외() {
        assertThatThrownBy(() -> command.notify(
                NotificationType.EMAIL_FAILED, COMPANY, RECIPIENT, "메일 실패", RefType.QUOTE, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("notifyForDeal 견적 4종 - 담당자가 활성이면 담당자에게만, refType=QUOTE")
    void notifyForDeal_활성_담당자에게만() {
        given(dealQuery.assigneeIdOf(DEAL)).willReturn(ASSIGNEE);
        given(memberQuery.isActive(ASSIGNEE)).willReturn(true);

        command.notifyForDeal(NotificationType.QUOTE_VIEWED, COMPANY, DEAL,
                "고객이 견적을 열람했습니다", QUOTE);

        Notification n = saved();
        assertThat(n.getRecipientMemberId()).isEqualTo(ASSIGNEE);
        assertThat(n.getType()).isEqualTo(Notification.Type.QUOTE_VIEWED);
        assertThat(n.getRefType()).isEqualTo("QUOTE");
        assertThat(n.getRefId()).isEqualTo(QUOTE);
    }

    @Test
    @DisplayName("notifyForDeal 견적 4종 - 담당자가 비활성이면 기업 관리자 전원에게 (Q-26 폴백)")
    void notifyForDeal_비활성_담당자면_관리자_폴백() {
        given(dealQuery.assigneeIdOf(DEAL)).willReturn(ASSIGNEE);
        given(memberQuery.isActive(ASSIGNEE)).willReturn(false);
        given(memberQuery.findAdminIds(COMPANY)).willReturn(List.of(ADMIN_1, ADMIN_2));

        command.notifyForDeal(NotificationType.QUOTE_APPROVED, COMPANY, DEAL,
                "견적이 승인되었습니다", QUOTE);

        assertThat(savedAll(2)).extracting(Notification::getRecipientMemberId)
                .containsExactly(ADMIN_1, ADMIN_2);
    }

    @Test
    @DisplayName("notifyForDeal INQUIRY_RECEIVED - 담당자와 기업 관리자 전원에게 (NT-10 union)")
    void notifyForDeal_문의는_담당자와_관리자_모두() {
        given(dealQuery.assigneeIdOf(DEAL)).willReturn(ASSIGNEE);
        given(memberQuery.isActive(ASSIGNEE)).willReturn(true);
        given(memberQuery.findAdminIds(COMPANY)).willReturn(List.of(ADMIN_1, ADMIN_2));

        command.notifyForDeal(NotificationType.INQUIRY_RECEIVED, COMPANY, DEAL,
                "고객이 문의를 남겼습니다", QUOTE);

        assertThat(savedAll(3)).extracting(Notification::getRecipientMemberId)
                .containsExactly(ASSIGNEE, ADMIN_1, ADMIN_2);
    }

    @Test
    @DisplayName("notifyForDeal INQUIRY_RECEIVED - 담당자가 관리자이기도 하면 중복 제거해 1행씩")
    void notifyForDeal_문의_수신자_중복_제거() {
        given(dealQuery.assigneeIdOf(DEAL)).willReturn(ADMIN_1);
        given(memberQuery.isActive(ADMIN_1)).willReturn(true);
        given(memberQuery.findAdminIds(COMPANY)).willReturn(List.of(ADMIN_1, ADMIN_2));

        command.notifyForDeal(NotificationType.INQUIRY_RECEIVED, COMPANY, DEAL,
                "고객이 문의를 남겼습니다", QUOTE);

        assertThat(savedAll(2)).extracting(Notification::getRecipientMemberId)
                .containsExactly(ADMIN_1, ADMIN_2);
    }

    @Test
    @DisplayName("notifyForDeal - 담당자 비활성 + 관리자도 없으면 저장 없이 조용히 끝낸다")
    void notifyForDeal_수신자가_아무도_없으면_무동작() {
        given(dealQuery.assigneeIdOf(DEAL)).willReturn(ASSIGNEE);
        given(memberQuery.isActive(ASSIGNEE)).willReturn(false);
        given(memberQuery.findAdminIds(COMPANY)).willReturn(List.of());

        command.notifyForDeal(NotificationType.QUOTE_REJECTED, COMPANY, DEAL,
                "견적이 반려되었습니다", QUOTE);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("notifyForDeal에 EMAIL_FAILED를 넘기면 예외 (Deal 컨텍스트가 아님)")
    void notifyForDeal_EMAIL_FAILED는_거부() {
        assertThatThrownBy(() -> command.notifyForDeal(
                NotificationType.EMAIL_FAILED, COMPANY, DEAL, "메일 실패", QUOTE))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("message가 500자를 넘으면 499자 + 줄임표로 잘라 저장한다")
    void message_500자_초과면_절삭() {
        given(dealQuery.assigneeIdOf(DEAL)).willReturn(ASSIGNEE);
        given(memberQuery.isActive(ASSIGNEE)).willReturn(true);

        command.notifyForDeal(NotificationType.QUOTE_VIEWED, COMPANY, DEAL, "가".repeat(600), QUOTE);

        String stored = saved().getMessage();
        assertThat(stored).hasSize(500);
        assertThat(stored).endsWith("…");
        assertThat(stored).startsWith("가".repeat(499));
    }

    @Test
    @DisplayName("message가 정확히 500자면 자르지 않는다")
    void message_500자면_그대로() {
        command.notify(NotificationType.EMAIL_FAILED, COMPANY, RECIPIENT, "나".repeat(500), null, null);

        assertThat(saved().getMessage()).isEqualTo("나".repeat(500));
    }
}
