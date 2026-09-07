package com.twojo.member.controller;

import com.twojo.member.dto.AcceptInvitationRequest;
import com.twojo.member.dto.InvitationInfoResponse;
import com.twojo.member.service.PublicInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대 링크 (07 §A · MB-03) — 비로그인 경로.
 *
 * <p>AccessContext를 받지 않는다. 이 체인은 permitAll이라 주입할 인증 정보가 없다.
 */
@RestController
@RequestMapping("/public/api/v1/invitations")
@RequiredArgsConstructor
public class PublicInvitationController {

    private final PublicInvitationService publicInvitationService;

    /** 확인 (MB-03) — 만료·취소된 링크는 INVITATION_NOT_PENDING. */
    @GetMapping("/{token}")
    public InvitationInfoResponse info(@PathVariable String token) {
        return publicInvitationService.info(token);
    }

    /** 수락 (MB-03) — 계정 생성. 응답 바디 없음, 로그인은 별도다. */
    @PostMapping("/{token}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable String token,
                       @Valid @RequestBody AcceptInvitationRequest request) {
        publicInvitationService.accept(token, request);
    }
}
