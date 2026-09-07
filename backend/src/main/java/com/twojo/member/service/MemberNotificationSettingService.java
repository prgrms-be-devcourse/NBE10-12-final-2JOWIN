package com.twojo.member.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.NotificationSettingCommand;
import com.twojo.boundary.NotificationSettingQuery;
import com.twojo.boundary.NotificationSettingType;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.member.dto.NotificationSettingResponse;
import com.twojo.member.dto.UpdateNotificationSettingsRequest;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림 수신 설정 (NT-07) — 메일 채널만.
 *
 * <p>설정 값은 알림 모듈이 들고 있어서 여기서는 계약 두 개로만 오간다. 화면이 쓰는 문자열 목록과
 * 계약이 쓰는 상수 집합을 서로 옮기는 것이 이 클래스가 하는 일의 전부다.
 *
 * <p>이름 앞에 Member를 붙인 이유는 알림 모듈에 같은 이름의 구현이 있어서다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberNotificationSettingService {

    private final NotificationSettingQuery notificationSettingQuery;
    private final NotificationSettingCommand notificationSettingCommand;

    /** 조회 — 저장한 적이 없어도 네 항목이 다 온다. 없는 항목을 켜진 것으로 채우는 일은 계약이 한다. */
    public NotificationSettingResponse get(AccessContext ctx) {
        Map<NotificationSettingType, Boolean> stored =
                notificationSettingQuery.settingsOf(ctx.memberId());

        return new NotificationSettingResponse(toEntries(stored));
    }

    /**
     * 전체 교체 후 저장된 값을 그대로 돌려준다.
     *
     * <p>돌려주는 값은 다시 조회한 것이 아니라 방금 넣은 것이다 — 같은 트랜잭션 안이라 결과가 같고,
     * 왕복을 한 번 줄인다.
     */
    @Transactional
    public NotificationSettingResponse replace(AccessContext ctx,
                                               UpdateNotificationSettingsRequest request) {
        Map<NotificationSettingType, Boolean> settings = toSettings(request.settings());

        notificationSettingCommand.replaceSettings(ctx.memberId(), settings);

        return new NotificationSettingResponse(toEntries(settings));
    }

    /**
     * 응답 순서를 상수 선언 순서로 고정한다 — 맵의 순회 순서에 화면 배치를 맡기지 않는다.
     *
     * <p>빠진 항목을 켜진 것으로 읽는다. 계약이 네 항목을 다 채워 주므로 실제로는 쓰이지 않는 자리인데,
     * 굳이 꺼진 쪽으로 두면 값이 비었을 때 구성원이 원한 적 없는 방향으로 기운다.
     */
    private List<NotificationSettingResponse.Entry> toEntries(
            Map<NotificationSettingType, Boolean> settings) {
        return Arrays.stream(NotificationSettingType.values())
                .map(type -> new NotificationSettingResponse.Entry(
                        type.name(), settings.getOrDefault(type, true)))
                .toList();
    }

    /**
     * 요청 목록을 계약이 받는 형태로 옮기면서 세 가지를 함께 본다 — 설정 대상이 맞는 이름인가,
     * 같은 항목이 두 번 왔는가, 네 항목이 다 왔는가.
     *
     * <p>세 검사를 여기에 두는 이유는 다른 호출 경로가 생겨도 뚫리지 않게 하기 위해서다.
     * 계약도 같은 것을 보지만 그쪽은 잘못 부른 코드를 잡는 자리라 사용자에게 보일 응답이 아니다.
     */
    private Map<NotificationSettingType, Boolean> toSettings(
            List<UpdateNotificationSettingsRequest.Entry> entries) {
        Map<NotificationSettingType, Boolean> settings =
                new EnumMap<>(NotificationSettingType.class);

        for (UpdateNotificationSettingsRequest.Entry entry : entries) {
            NotificationSettingType type = parseType(entry.type());
            if (settings.put(type, entry.emailEnabled()) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
        if (settings.size() != NotificationSettingType.values().length) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return settings;
    }

    /**
     * 설정 대상이 아닌 이름은 400이다.
     *
     * <p>{@code QUOTE_APPROVED}처럼 알림 종류에는 있지만 설정 대상이 아닌 이름이 그럴듯하게 들어온다.
     * 잡지 않으면 변환이 던지는 예외가 그대로 올라가 500이 된다.
     */
    private NotificationSettingType parseType(String type) {
        try {
            return NotificationSettingType.valueOf(type);
        } catch (IllegalArgumentException notASettingType) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
