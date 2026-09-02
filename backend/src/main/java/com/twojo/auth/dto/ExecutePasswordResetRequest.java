package com.twojo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 재설정 실행 요청 (08 §A · AU-05).
 *
 * <p>RESET·INITIAL_SETUP 공용이다 — 토큰의 purpose로 구분되므로 요청 형태는 같다 (Q-33).
 *
 * <p>memberId가 없다. 토큰 행에서 나오기 때문이다 — 요청에 두면 남의 계정을
 * 바꾸는 요청을 만들 수 있다.
 */
public record ExecutePasswordResetRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String newPassword) {}
