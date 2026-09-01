package com.twojo.member.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.member.dto.MeResponse;
import com.twojo.member.service.MeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보 (07 §A). PATCH·비밀번호 변경은 별도 항목이다. */
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
}
