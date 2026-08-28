package com.twojo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 로그인 요청 (08 §A). rememberMe 가 refresh 수명을 가른다 — 12h / 14d (AU-10, Q-32). */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        boolean rememberMe) {}
