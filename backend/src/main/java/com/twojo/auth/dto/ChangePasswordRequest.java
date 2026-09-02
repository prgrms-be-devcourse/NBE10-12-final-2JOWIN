package com.twojo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청 (08 §A · AU-04).
 *
 * <p>바꿀 대상(memberId)이 없다. access token에서 오기 때문이다 — 09 "본인 것만".
 * 필드를 두면 남의 비밀번호를 바꾸는 요청을 만들 수 있게 된다.
 *
 * <p>currentPassword에 @Size를 걸지 않는다. 걸면 8자 미만은 400, 8자 이상 틀리면 422로
 * 응답이 갈려 기존 비밀번호의 길이를 추측할 수 있다.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword) {}
