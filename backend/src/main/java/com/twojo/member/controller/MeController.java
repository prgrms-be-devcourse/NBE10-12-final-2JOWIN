package com.twojo.member.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.member.dto.MeResponse;
import com.twojo.member.dto.UpdateMeRequest;
import com.twojo.member.service.MeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보 (07 §A). 비밀번호 변경은 auth 소유다. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    /** 세션 확인 (AU-03·07) — memberId는 토큰에서 온다. 요청에 조회 키가 없다. */
    @GetMapping
    public MeResponse me(AccessContext ctx) {
        return meService.get(ctx);
    }

    /** 프로필 수정 (AU-07) — 대상은 토큰의 구성원이다. */
    @PatchMapping
    public MeResponse update(AccessContext ctx, @Valid @RequestBody UpdateMeRequest request) {
        return meService.update(ctx, request);
    }
}
