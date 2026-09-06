package com.twojo.member.dto;

import com.twojo.member.entity.Invitation;
import java.time.Instant;
import java.util.UUID;

/**
 * 초대 응답 (08 §A).
 *
 * <p>토큰은 담지 않는다. 원문은 DB에 없고, 해시를 내보내면 링크를 받지 않은 관리자가
 * 수락 경로를 만들 수 있게 된다.
 */
public record InvitationResponse(
        UUID id,
        String email,
        String role,
        String status,
        Instant expiresAt,
        Instant createdAt) {

    public static InvitationResponse of(Invitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getEmail(),
                invitation.getRole().name(), invitation.getStatus().name(),
                invitation.getExpiresAt(), invitation.getCreatedAt());
    }
}
