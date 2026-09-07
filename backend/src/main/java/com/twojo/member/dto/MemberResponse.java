package com.twojo.member.dto;

import com.twojo.member.entity.Member;
import java.time.Instant;
import java.util.UUID;

/**
 * 구성원 응답 (08 §A · MB-07).
 *
 * <p>status는 ACTIVE / INACTIVE 두 값이다. 목록에 비활성 구성원도 함께 나오므로
 * 이 필드가 둘을 가른다 — 삭제가 없어 비활성이 최종 상태다.
 */
public record MemberResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String role,
        String status,
        Instant createdAt) {

    /** 엔티티 → 응답. 응답 모양이 바뀔 때 열 파일을 하나로 둔다 — 서비스는 판단만 담는다. */
    public static MemberResponse of(Member member) {
        return new MemberResponse(member.getId(), member.getName(), member.getEmail(),
                member.getPhone(), member.getRole().name(), member.getStatus().name(),
                member.getCreatedAt());
    }
}
