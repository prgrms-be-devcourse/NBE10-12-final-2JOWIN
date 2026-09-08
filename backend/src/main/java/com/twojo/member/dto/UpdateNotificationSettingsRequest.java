package com.twojo.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 알림 수신 설정 변경 요청 (08 §A · NT-07) — 전체 교체다.
 *
 * <p>일부만 보내는 것을 허용하지 않는다. 안 보낸 항목을 어떻게 다룰지에서 갈리면 구성원이 예전에 끈
 * 설정이 저절로 켜져 있는 일이 생긴다.
 *
 * <p>{@code @NotEmpty}는 "비어 있지 않다"까지만 본다. 네 항목이 정확히 한 번씩 들어왔는지와 type이
 * 실제 설정 대상인지는 서비스가 판정한다.
 */
public record UpdateNotificationSettingsRequest(@NotEmpty List<Entry> settings) {

    public record Entry(@NotBlank String type, boolean emailEnabled) {}
}
