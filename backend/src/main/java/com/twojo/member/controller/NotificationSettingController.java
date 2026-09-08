package com.twojo.member.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.member.dto.NotificationSettingResponse;
import com.twojo.member.dto.UpdateNotificationSettingsRequest;
import com.twojo.member.service.MemberNotificationSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 알림 수신 설정 (07 §A · NT-07) — 전 구성원.
 *
 * <p>대상은 토큰의 구성원이다. 남의 설정을 가리킬 경로가 없어 회사 스코프 판정이 따로 없다.
 */
@RestController
@RequestMapping("/api/v1/me/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingController {

    private final MemberNotificationSettingService memberNotificationSettingService;

    /** 조회 — 저장한 적이 없어도 네 항목이 다 온다. */
    @GetMapping
    public NotificationSettingResponse get(AccessContext ctx) {
        return memberNotificationSettingService.get(ctx);
    }

    /** 전체 교체 — 네 항목을 다 보낸다. 일부만 보내면 400이다. */
    @PutMapping
    public NotificationSettingResponse replace(
            AccessContext ctx, @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return memberNotificationSettingService.replace(ctx, request);
    }
}
