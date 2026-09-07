package com.twojo.member.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.global.response.PageResponse;
import com.twojo.member.dto.CreateInvitationRequest;
import com.twojo.member.dto.InvitationResponse;
import com.twojo.member.entity.Invitation;
import com.twojo.member.service.InvitationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대 (07 §A · MB) — 기업 관리자 전용. 링크로 들어오는 조회·수락은 PublicInvitationController다.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int MAX_PAGE_SIZE = 100;

    /** 초대는 이름이 없어 최근 보낸 순이다 — 구성원 목록의 name ASC와 다르다 (Q-39). */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final InvitationService invitationService;

    /** 발송 (MB-01·02) — 역할 지정 필수. 이미 계정이 있거나 대기 초대가 있는 이메일은 막힌다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(AccessContext ctx,
                                     @Valid @RequestBody CreateInvitationRequest request) {
        return invitationService.create(ctx, request);
    }

    /** 목록 — status를 비우면 종결된 초대도 함께 나온다. */
    @GetMapping
    public PageResponse<InvitationResponse> list(
            AccessContext ctx,
            @RequestParam(required = false) Invitation.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return invitationService.list(ctx, status, pageable(page, size));
    }

    /** 재발송 (MB-06) — 옛 초대는 만료되고 새 초대가 응답으로 돌아온다 (Q-31). */
    @PostMapping("/{invitationId}/resend")
    public InvitationResponse resend(AccessContext ctx, @PathVariable UUID invitationId) {
        return invitationService.resend(ctx, invitationId);
    }

    /** 취소 (MB-05) — 대기 중인 초대만. */
    @PostMapping("/{invitationId}/cancel")
    public InvitationResponse cancel(AccessContext ctx, @PathVariable UUID invitationId) {
        return invitationService.cancel(ctx, invitationId);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
    }
}
