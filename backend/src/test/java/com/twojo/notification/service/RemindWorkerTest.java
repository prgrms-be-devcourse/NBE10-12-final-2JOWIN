package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MailCommand.TemplateType;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.MemberQuery.MemberContact;
import com.twojo.boundary.NotificationCommand;
import com.twojo.boundary.NotificationCommand.NotificationType;
import com.twojo.boundary.NotificationCommand.RefType;
import com.twojo.boundary.NotificationSettingQuery;
import com.twojo.boundary.NotificationSettingType;
import com.twojo.boundary.QuoteQuery;
import com.twojo.notification.entity.Notification;
import com.twojo.notification.repository.NotificationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * {@link RemindWorker} — 견적 1건의 멱등 가드, 수신자 순회, NT-07 설정에 따른 메일 병행, 이메일 정규화를 검증한다.
 * {@code REQUIRES_NEW} 트랜잭션 경계와 실 PG 저장은 {@link RemindNoResponseIntegrationTest}가 덮는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RemindWorkerTest {

    private static final UUID COMPANY = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID DEAL = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID QUOTE = UUID.fromString("6b000000-0000-4000-8000-000000000001");
    private static final UUID MEMBER_1 = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID MEMBER_2 = UUID.fromString("a0000000-0000-4000-8000-000000000002");

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationCommand notificationCommand;
    @Mock
    private MailCommand mailCommand;
    @Mock
    private NotificationSettingQuery notificationSettingQuery;
    @Mock
    private MemberQuery memberQuery;
    @InjectMocks
    private RemindWorker worker;

    private QuoteQuery.QuoteSummary quote() {
        return new QuoteQuery.QuoteSummary(QUOTE, "Q-NT05-001", DEAL, COMPANY, null,
                Instant.parse("2026-09-01T00:00:00Z"), null, LocalDate.of(2026, 9, 30));
    }

    private void guardClear() {
        given(notificationRepository.existsByCompanyIdAndTypeAndRefId(
                COMPANY, Notification.Type.REMIND_NO_RESPONSE, QUOTE)).willReturn(false);
    }

    private void mailOn(UUID memberId) {
        given(notificationSettingQuery.settingsOf(memberId))
                .willReturn(Map.of(NotificationSettingType.REMIND_NO_RESPONSE, true));
    }

    @Test
    @DisplayName("가드 미존재 - 담당자에게 인앱 알림 + 병행 메일을 예약한다")
    void 리마인드_인앱과_메일() {
        guardClear();
        given(notificationCommand.dealRecipients(NotificationType.REMIND_NO_RESPONSE, COMPANY, DEAL))
                .willReturn(List.of(MEMBER_1));
        mailOn(MEMBER_1);
        given(memberQuery.getContact(MEMBER_1))
                .willReturn(new MemberContact("담당자", "rep@twojo.test", "010-0000-0000"));

        worker.remind(COMPANY, quote());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        then(notificationCommand).should().notify(eq(NotificationType.REMIND_NO_RESPONSE), eq(COMPANY),
                eq(MEMBER_1), message.capture(), eq(RefType.QUOTE), eq(QUOTE));
        assertThat(message.getValue()).contains("Q-NT05-001");
        then(mailCommand).should().schedule(eq(TemplateType.QUOTE_REMIND), eq(COMPANY),
                eq("rep@twojo.test"), eq(QUOTE), anyString(), anyString());
    }

    @Test
    @DisplayName("가드 존재 - 알림도 메일도 만들지 않는다 (견적당 1회)")
    void 이미_리마인드했으면_스킵() {
        given(notificationRepository.existsByCompanyIdAndTypeAndRefId(
                COMPANY, Notification.Type.REMIND_NO_RESPONSE, QUOTE)).willReturn(true);

        worker.remind(COMPANY, quote());

        then(notificationCommand).shouldHaveNoInteractions();
        then(mailCommand).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("NT-07 REMIND_NO_RESPONSE OFF - 인앱은 만들되 메일은 예약하지 않는다")
    void 수신_설정_꺼져있으면_메일_생략() {
        guardClear();
        given(notificationCommand.dealRecipients(NotificationType.REMIND_NO_RESPONSE, COMPANY, DEAL))
                .willReturn(List.of(MEMBER_1));
        given(notificationSettingQuery.settingsOf(MEMBER_1))
                .willReturn(Map.of(NotificationSettingType.REMIND_NO_RESPONSE, false));

        worker.remind(COMPANY, quote());

        then(notificationCommand).should().notify(any(), any(), any(), any(), any(), any());
        then(mailCommand).shouldHaveNoInteractions();
        then(memberQuery).should(never()).getContact(any());
    }

    @Test
    @DisplayName("수신 이메일을 trim·소문자로 정규화해 예약한다")
    void 이메일_정규화() {
        guardClear();
        given(notificationCommand.dealRecipients(NotificationType.REMIND_NO_RESPONSE, COMPANY, DEAL))
                .willReturn(List.of(MEMBER_1));
        mailOn(MEMBER_1);
        given(memberQuery.getContact(MEMBER_1))
                .willReturn(new MemberContact("담당자", "  Rep@TwoJo.TEST  ", "010-0000-0000"));

        worker.remind(COMPANY, quote());

        then(mailCommand).should().schedule(any(), any(), eq("rep@twojo.test"), any(), any(), any());
    }

    @Test
    @DisplayName("수신자가 없으면 인앱도 메일도 만들지 않는다")
    void 수신자_없으면_무동작() {
        guardClear();
        given(notificationCommand.dealRecipients(NotificationType.REMIND_NO_RESPONSE, COMPANY, DEAL))
                .willReturn(List.of());

        worker.remind(COMPANY, quote());

        then(notificationCommand).should(never()).notify(any(), any(), any(), any(), any(), any());
        then(mailCommand).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("수신자 2명이면 각자에게 인앱 + 메일")
    void 수신자_여러명() {
        guardClear();
        given(notificationCommand.dealRecipients(NotificationType.REMIND_NO_RESPONSE, COMPANY, DEAL))
                .willReturn(List.of(MEMBER_1, MEMBER_2));
        mailOn(MEMBER_1);
        mailOn(MEMBER_2);
        given(memberQuery.getContact(MEMBER_1))
                .willReturn(new MemberContact("A", "a@twojo.test", "010-0000-0001"));
        given(memberQuery.getContact(MEMBER_2))
                .willReturn(new MemberContact("B", "b@twojo.test", "010-0000-0002"));

        worker.remind(COMPANY, quote());

        then(notificationCommand).should(times(2)).notify(any(), any(), any(), any(), any(), any());
        then(mailCommand).should(times(2)).schedule(any(), any(), any(), any(), any(), any());
    }
}
