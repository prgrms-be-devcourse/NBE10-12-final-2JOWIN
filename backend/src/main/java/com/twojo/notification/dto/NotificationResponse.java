package com.twojo.notification.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 인앱 알림 응답 (NT-08 · 폴링 30초).
 * type은 문자열 — QUOTE_VIEWED / QUOTE_APPROVED / QUOTE_REJECTED / REMIND_NO_RESPONSE
 * / INQUIRY_RECEIVED / EMAIL_FAILED(NT-12). refType·refId는 클릭 이동 대상.
 */
public record NotificationResponse(
        UUID id,
        String type,
        String message,
        String refType,
        UUID refId,
        Instant readAt,
        Instant createdAt) {}
