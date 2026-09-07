package com.twojo.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 초대 수락 요청 (08 §A · MB-03).
 *
 * <p>이메일과 역할이 없다. 둘 다 초대에 박혀 있어 받는 사람이 바꿀 수 없다 — 필드를 두면
 * 링크 하나로 다른 이메일의 계정을 만들 수 있게 된다.
 */
public record AcceptInvitationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(min = 8) String password) {}
