package com.twojo.auth.controller;

import com.twojo.auth.dto.ChangePasswordRequest;
import com.twojo.auth.service.PasswordService;
import com.twojo.boundary.AccessContext;
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
 * 비밀번호 변경 (07 §A · AU-04) — 인증이 필요한 경로다 (memberChain).
 *
 * <p>경로는 /api/v1/me/password지만 클래스는 auth 모듈에 둔다. member에 두면
 * member -> auth.service 참조가 되어 auth/package-info의 "루트 공개 인터페이스만"에 걸린다.
 * 경로와 소유 모듈은 별개다.
 */
@RestController
@RequestMapping("/api/v1/me/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    /**
     * 비밀번호 변경 (AU-04) — 성공 시 그 구성원의 refresh_token 전 행이 폐기된다 (05 §9).
     * 본인도 재로그인해야 한다 (07 §A v1.6.5).
     *
     * <p>바꿀 대상은 요청이 아니라 AccessContext에서 온다 — 09 "본인 것만".
     *
     * <p>쿠키는 지우지 않는다. 07 쿠키 규약표가 삭제 시점을 로그아웃 하나로 규정하고,
     * 남겨 둬도 다음 재발급이 401로 정리한다 (AU-12).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(AccessContext ctx, @Valid @RequestBody ChangePasswordRequest request) {
        passwordService.change(ctx.memberId(), request, Instant.now());
    }
}
