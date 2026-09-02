package com.twojo.activity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 감사 로그 목록 응답 (AC-11) — <b>payload를 싣지 않는다.</b>
 *
 * <p>목록은 "누가·언제·무엇을"만 보여주면 되고, 변경 전후 값은 상세에서 펼친다
 * ({@link AuditLogDetailResponse}). 목록에 payload를 실으면 응답이 무거워진다.
 *
 * <p>{@code actorType}은 문자열이다 — {@code MEMBER} / {@code PLATFORM_ADMIN} /
 * {@code CUSTOMER_LINK} / {@code SYSTEM}. 응답 DTO가 boundary enum을 그대로 노출하면
 * 계약 변경이 API 스키마를 흔들어서 문자열로 내보낸다.
 */
public record AuditLogResponse(
        UUID id,
        String entityType,
        UUID entityId,
        String eventType,
        String actorType,
        UUID actorId,
        String actorName,
        Instant occurredAt) {}
