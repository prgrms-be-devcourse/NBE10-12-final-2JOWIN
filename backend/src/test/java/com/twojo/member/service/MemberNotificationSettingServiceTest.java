package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.NotificationSettingCommand;
import com.twojo.boundary.NotificationSettingQuery;
import com.twojo.boundary.NotificationSettingType;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.error.ErrorResponse;
import com.twojo.member.dto.NotificationSettingResponse;
import com.twojo.member.dto.UpdateNotificationSettingsRequest;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 알림 수신 설정 (NT-07) — 설정 대상 4종 · 전체 교체 · 응답 순서.
 *
 * <p>값을 실제로 읽고 쓰는 것은 알림 모듈이라 여기서 고정하는 것은 그 통로로 오가는 형태다 —
 * 무엇이 유효한 요청인지, 화면에 어떤 순서로 나가는지.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberNotificationSettingServiceTest {

    private static final UUID 한빛오피스 = UUID.randomUUID();
    private static final UUID 김서연 = UUID.randomUUID();

    private static final AccessContext 김서연_관리자 =
            new AccessContext(한빛오피스, 김서연, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);

    @Mock private NotificationSettingQuery notificationSettingQuery;
    @Mock private NotificationSettingCommand notificationSettingCommand;

    @InjectMocks private MemberNotificationSettingService memberNotificationSettingService;

    @Nested
    class 설정_조회는 {

        /** 06 notification_setting 주석 · 11 §5 — 한 번도 저장한 적 없는 구성원이 기본 상태다. */
        @Test
        void 저장한_적이_없어도_네_항목이_모두_켜진_채로_나온다() {
            // given — 저장 행이 없어 계약이 네 항목을 켜진 값으로 채워 준다
            given(notificationSettingQuery.settingsOf(김서연)).willReturn(모두(true));

            // when — 김서연이 설정 화면을 연다
            NotificationSettingResponse 응답 = memberNotificationSettingService.get(김서연_관리자);

            // then — 네 항목이 다 오고 전부 켜져 있다
            assertThat(응답.settings())
                    .hasSize(4)
                    .allMatch(NotificationSettingResponse.Entry::emailEnabled);
        }

        /** 07 v1.6.11 각주 4 — 순서가 흔들리면 화면의 토글이 자리를 바꾼다. */
        @Test
        void 응답_순서는_설정_대상_선언_순서를_따른다() {
            // given — 계약이 선언과 반대 순서로 담아 준다
            Map<NotificationSettingType, Boolean> 뒤집힌_순서 = new LinkedHashMap<>();
            뒤집힌_순서.put(NotificationSettingType.INQUIRY_RECEIVED, true);
            뒤집힌_순서.put(NotificationSettingType.REMIND_NO_RESPONSE, false);
            뒤집힌_순서.put(NotificationSettingType.QUOTE_RESPONDED, true);
            뒤집힌_순서.put(NotificationSettingType.QUOTE_VIEWED, false);
            given(notificationSettingQuery.settingsOf(김서연)).willReturn(뒤집힌_순서);

            // when — 설정을 조회하면
            NotificationSettingResponse 응답 = memberNotificationSettingService.get(김서연_관리자);

            // then — 받은 순서가 아니라 선언 순서로 나간다
            assertThat(응답.settings())
                    .extracting(NotificationSettingResponse.Entry::type)
                    .containsExactly("QUOTE_VIEWED", "QUOTE_RESPONDED",
                            "REMIND_NO_RESPONSE", "INQUIRY_RECEIVED");
        }
    }

    @Nested
    class 설정_변경은 {

        /** 07 v1.6.11 각주 4 — 일부만 받으면 안 보낸 항목이 어떻게 될지에서 갈린다. */
        @Test
        void 네_항목을_다_보내지_않으면_변경할_수_없다() {
            // given — 세 항목만 담아 보낸다
            UpdateNotificationSettingsRequest 요청 = 요청(
                    항목("QUOTE_VIEWED", false),
                    항목("QUOTE_RESPONDED", true),
                    항목("REMIND_NO_RESPONSE", false));

            // when · then — 400으로 막힌다
            변경이_막힌다(요청);
        }

        /** 뒤 값이 앞 값을 덮어써 네 개를 보냈는데 셋만 반영된다 — 길이 검사로는 안 잡힌다. */
        @Test
        void 같은_항목을_두_번_보내면_변경할_수_없다() {
            // given — 네 개지만 QUOTE_VIEWED가 두 번이고 INQUIRY_RECEIVED가 없다
            UpdateNotificationSettingsRequest 요청 = 요청(
                    항목("QUOTE_VIEWED", false),
                    항목("QUOTE_VIEWED", true),
                    항목("QUOTE_RESPONDED", true),
                    항목("REMIND_NO_RESPONSE", false));

            변경이_막힌다(요청);
        }

        /** Q-35 — QUOTE_APPROVED는 알림 종류에는 있고 설정 대상에는 없다. 안 잡으면 500이 된다. */
        @Test
        void 설정_대상이_아닌_이름은_변경할_수_없다() {
            // given — 그럴듯하지만 끌 수 없는 알림의 이름이 섞여 들어온다
            UpdateNotificationSettingsRequest 요청 = 요청(
                    항목("QUOTE_VIEWED", false),
                    항목("QUOTE_APPROVED", true),
                    항목("REMIND_NO_RESPONSE", false),
                    항목("INQUIRY_RECEIVED", true));

            변경이_막힌다(요청);
        }

        /** NT-07 — 요청을 계약이 받는 형태로 옮기는 것이 이 서비스의 본체다. */
        @Test
        void 전체_교체는_보낸_값을_그대로_저장한다() {
            // given · when — 켜짐과 꺼짐이 섞인 네 항목을 보낸다
            memberNotificationSettingService.replace(김서연_관리자, 섞인_네_항목());

            // then — 계약에 같은 값이 그대로 넘어간다
            assertThat(저장된_설정())
                    .containsEntry(NotificationSettingType.QUOTE_VIEWED, false)
                    .containsEntry(NotificationSettingType.QUOTE_RESPONDED, true)
                    .containsEntry(NotificationSettingType.REMIND_NO_RESPONSE, false)
                    .containsEntry(NotificationSettingType.INQUIRY_RECEIVED, true);
        }

        /** 재조회 없이 방금 넣은 값을 돌려주는 것은 구현 판단이다 — 같은 트랜잭션이라 결과가 같다. */
        @Test
        void 교체_응답은_방금_저장한_값을_보여준다() {
            NotificationSettingResponse 응답 =
                    memberNotificationSettingService.replace(김서연_관리자, 섞인_네_항목());

            assertThat(응답.settings()).containsExactly(
                    new NotificationSettingResponse.Entry("QUOTE_VIEWED", false),
                    new NotificationSettingResponse.Entry("QUOTE_RESPONDED", true),
                    new NotificationSettingResponse.Entry("REMIND_NO_RESPONSE", false),
                    new NotificationSettingResponse.Entry("INQUIRY_RECEIVED", true));
            then(notificationSettingQuery).shouldHaveNoInteractions();
        }

        /** 400인데 일부가 저장되면 화면과 저장된 값이 갈린다. */
        @Test
        void 검증에_실패하면_저장_통로에_닿지_않는다() {
            UpdateNotificationSettingsRequest 요청 = 요청(
                    항목("QUOTE_VIEWED", false),
                    항목("없는이름", true),
                    항목("REMIND_NO_RESPONSE", false),
                    항목("INQUIRY_RECEIVED", true));

            변경이_막힌다(요청);

            then(notificationSettingCommand).shouldHaveNoInteractions();
        }

        /**
         * 세 검증 모두 400이면서 <b>어느 필드가 틀렸는지</b>를 실어야 한다 —
         * 07 부록이 VALIDATION_FAILED에 "fieldErrors 참조"라고 적는다.
         *
         * <p>가리키는 것은 개별 Entry가 아니라 {@code settings} 목록 전체다. 전체 교체라
         * 목록이 한 덩어리이고, 화면이 취할 행동도 "이 목록을 다시 만든다" 하나다.
         */
        private void 변경이_막힌다(UpdateNotificationSettingsRequest 요청) {
            assertThatThrownBy(() -> memberNotificationSettingService.replace(김서연_관리자, 요청))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException 예외 = (BusinessException) e;
                        assertThat(예외.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                        assertThat(예외.getFieldErrors())
                                .singleElement()
                                .extracting(ErrorResponse.FieldError::field)
                                .isEqualTo("settings");
                    });
        }

        @SuppressWarnings("unchecked")
        private Map<NotificationSettingType, Boolean> 저장된_설정() {
            ArgumentCaptor<Map<NotificationSettingType, Boolean>> 저장 =
                    ArgumentCaptor.forClass(Map.class);
            then(notificationSettingCommand).should().replaceSettings(eq(김서연), 저장.capture());
            return 저장.getValue();
        }
    }

    private static UpdateNotificationSettingsRequest 섞인_네_항목() {
        return 요청(
                항목("QUOTE_VIEWED", false),
                항목("QUOTE_RESPONDED", true),
                항목("REMIND_NO_RESPONSE", false),
                항목("INQUIRY_RECEIVED", true));
    }

    private static UpdateNotificationSettingsRequest.Entry 항목(String type, boolean enabled) {
        return new UpdateNotificationSettingsRequest.Entry(type, enabled);
    }

    private static UpdateNotificationSettingsRequest 요청(
            UpdateNotificationSettingsRequest.Entry... 항목들) {
        return new UpdateNotificationSettingsRequest(List.of(항목들));
    }

    private static Map<NotificationSettingType, Boolean> 모두(boolean enabled) {
        Map<NotificationSettingType, Boolean> settings =
                new EnumMap<>(NotificationSettingType.class);
        for (NotificationSettingType type : NotificationSettingType.values()) {
            settings.put(type, enabled);
        }
        return settings;
    }
}
