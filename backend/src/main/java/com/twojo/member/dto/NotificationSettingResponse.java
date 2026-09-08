package com.twojo.member.dto;

import java.util.List;

/**
 * 알림 수신 설정 조회 응답 (08 §A · NT-07).
 *
 * <p>메일 채널만 다룬다 — 인앱은 항상 기록되고 끌 수 없다.
 *
 * <p>순서는 설정 대상 상수의 선언 순서로 고정한다. 07·08에 순서 규정이 없는데, 요청마다 순서가
 * 흔들리면 화면의 토글이 자리를 바꾼다.
 */
public record NotificationSettingResponse(List<Entry> settings) {

    /** 한 번도 저장한 적이 없으면 네 항목이 모두 켜진 상태로 나온다. */
    public record Entry(String type, boolean emailEnabled) {}
}
