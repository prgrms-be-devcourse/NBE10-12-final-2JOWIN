package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.twojo.boundary.NotificationSettingType;
import com.twojo.notification.entity.NotificationSetting;
import com.twojo.notification.repository.NotificationSettingRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationSettingServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Mock
    private NotificationSettingRepository repository;
    @InjectMocks
    private NotificationSettingService service;

    @Test
    @DisplayName("settingsOf - 저장된 행이 없으면 4종 전부 ON")
    void settingsOf_행이_없으면_전부_ON() {
        given(repository.findByMemberId(MEMBER_ID)).willReturn(List.of());

        Map<NotificationSettingType, Boolean> result = service.settingsOf(MEMBER_ID);

        assertThat(result).hasSize(4);
        assertThat(result.values()).containsOnly(true);
    }

    @Test
    @DisplayName("settingsOf - 일부 행만 있으면 그 값 + 나머지는 ON")
    void settingsOf_일부만_저장돼_있으면_나머지_ON() {
        given(repository.findByMemberId(MEMBER_ID)).willReturn(List.of(
                NotificationSetting.of(MEMBER_ID, "QUOTE_VIEWED", false)));

        Map<NotificationSettingType, Boolean> result = service.settingsOf(MEMBER_ID);

        assertThat(result).hasSize(4);
        assertThat(result.get(NotificationSettingType.QUOTE_VIEWED)).isFalse();
        assertThat(result.get(NotificationSettingType.QUOTE_RESPONDED)).isTrue();
        assertThat(result.get(NotificationSettingType.REMIND_NO_RESPONSE)).isTrue();
        assertThat(result.get(NotificationSettingType.INQUIRY_RECEIVED)).isTrue();
    }

    @Test
    @DisplayName("settingsOf - 현재 enum에 없는 type 행은 무시하고 4종만 반환")
    void settingsOf_폐기_type_행은_무시() {
        given(repository.findByMemberId(MEMBER_ID)).willReturn(List.of(
                NotificationSetting.of(MEMBER_ID, "OBSOLETE_TYPE", false),
                NotificationSetting.of(MEMBER_ID, "QUOTE_VIEWED", false)));

        Map<NotificationSettingType, Boolean> result = service.settingsOf(MEMBER_ID);

        assertThat(result).hasSize(4);
        assertThat(result.get(NotificationSettingType.QUOTE_VIEWED)).isFalse();
    }

    @Test
    @DisplayName("replaceSettings - 4종을 각각 upsert한다")
    void replaceSettings_4종_upsert() {
        service.replaceSettings(MEMBER_ID, fullMap(true, false, true, false));

        verify(repository).upsert(any(), eq(MEMBER_ID), eq("QUOTE_VIEWED"), eq(true));
        verify(repository).upsert(any(), eq(MEMBER_ID), eq("QUOTE_RESPONDED"), eq(false));
        verify(repository).upsert(any(), eq(MEMBER_ID), eq("REMIND_NO_RESPONSE"), eq(true));
        verify(repository).upsert(any(), eq(MEMBER_ID), eq("INQUIRY_RECEIVED"), eq(false));
    }

    @Test
    @DisplayName("replaceSettings - 4종 중 하나가 빠지면 IllegalArgumentException, upsert 안 함")
    void replaceSettings_불완전_맵이면_예외() {
        Map<NotificationSettingType, Boolean> partial = new EnumMap<>(NotificationSettingType.class);
        partial.put(NotificationSettingType.QUOTE_VIEWED, true);

        assertThatThrownBy(() -> service.replaceSettings(MEMBER_ID, partial))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).upsert(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("replaceSettings - 값이 null이면 IllegalArgumentException")
    void replaceSettings_null_값이면_예외() {
        Map<NotificationSettingType, Boolean> withNull = fullMap(true, true, true, true);
        withNull.put(NotificationSettingType.INQUIRY_RECEIVED, null);

        assertThatThrownBy(() -> service.replaceSettings(MEMBER_ID, withNull))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).upsert(any(), any(), any(), anyBoolean());
    }

    private static Map<NotificationSettingType, Boolean> fullMap(
            boolean viewed, boolean responded, boolean remind, boolean inquiry) {
        Map<NotificationSettingType, Boolean> map = new EnumMap<>(NotificationSettingType.class);
        map.put(NotificationSettingType.QUOTE_VIEWED, viewed);
        map.put(NotificationSettingType.QUOTE_RESPONDED, responded);
        map.put(NotificationSettingType.REMIND_NO_RESPONSE, remind);
        map.put(NotificationSettingType.INQUIRY_RECEIVED, inquiry);
        return map;
    }
}
