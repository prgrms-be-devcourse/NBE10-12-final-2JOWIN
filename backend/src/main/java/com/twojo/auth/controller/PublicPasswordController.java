package com.twojo.auth.controller;

import com.twojo.auth.dto.ExecutePasswordResetRequest;
import com.twojo.auth.dto.RequestPasswordResetRequest;
import com.twojo.auth.service.PasswordService;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 재설정 (07 §A · AU-05) — 비로그인 경로다 (publicChain, SC-07~09).
 *
 * <p>인증이 없으므로 AccessContext를 받지 않는다. 자격 증명은 요청 자체에 있다 —
 * 요청은 이메일, 실행은 재설정 토큰.
 *
 * <p>변경(AU-04)과 클래스를 나눈 이유가 이것이다. 한 클래스에 섞으면 메서드마다
 * 경로를 봐야 인증 여부를 알 수 있다.
 */
@RestController
@RequestMapping("/public/api/v1/auth")
@RequiredArgsConstructor
public class PublicPasswordController {

    private final PasswordService passwordService;

    /**
     * 재설정 메일 요청 (AU-05) — <b>미가입 이메일도 같은 202다</b> (SC-09 인증 확장).
     * 응답이 갈리면 인증 없이 부를 수 있는 이 엔드포인트로 가입 여부를 훑을 수 있다.
     *
     * <p>202는 "접수했다"는 뜻이라, 계정이 있었는지 메일이 실제로 갔는지를 말하지 않는
     * 상태와 맞는다 (08 §A).
     */
    @PostMapping("/password-reset-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody RequestPasswordResetRequest request) {
        passwordService.requestReset(request, Instant.now());
    }

    /**
     * 재설정 실행 (AU-05) — RESET·INITIAL_SETUP 공용 (Q-33).
     * 없는 토큰과 못 쓰는 토큰이 같은 409 RESET_TOKEN_NOT_ACTIVE다 (05 §10).
     */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void executeReset(@Valid @RequestBody ExecutePasswordResetRequest request) {
        passwordService.executeReset(request, Instant.now());
    }
}
