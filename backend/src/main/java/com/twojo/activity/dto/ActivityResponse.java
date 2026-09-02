package com.twojo.activity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 상담 기록 응답 (AC-06·07).
 *
 * <p>{@code type}은 {@code MANUAL}(구성원이 직접 쓴 상담 기록) / {@code AUTO}(감사 로그에서
 * 병합된 자동 기록) 두 값이다. 딜 타임라인은 둘을 시간순으로 합쳐 보여준다 (AC-06).
 *
 * <p>{@code authorMemberName}·{@code authorActive}는 <b>표시용</b>이며 A의
 * {@code MemberQuery.get()}으로 채운다. 작성자가 비활성화돼도 기록은 그대로 남는다 (AC-08) —
 * 담당이 이관돼도 과거 상담을 누가 썼는지는 사실이라 바뀌지 않는다.
 */
public record ActivityResponse(
        UUID id,
        String type,
        String channel,
        String content,
        UUID authorMemberId,
        String authorMemberName,
        boolean authorActive,
        Instant occurredAt) {}
