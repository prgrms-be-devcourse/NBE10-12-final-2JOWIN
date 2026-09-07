package com.twojo.member.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.global.response.PageResponse;
import com.twojo.member.dto.ChangeRoleRequest;
import com.twojo.member.dto.MemberOptionResponse;
import com.twojo.member.dto.MemberResponse;
import com.twojo.member.service.MemberAdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구성원 (07 §A · MB).
 *
 * <p>역할 판정은 서비스가 한다 — 웹 계층에만 걸린 검사는 다른 호출 경로가 생기면 뚫린다.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int MAX_PAGE_SIZE = 100;

    /** 찾는 대상이라 이름순이다 — 딜·활동의 createdAt DESC와 다르다 (Q-39). */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final MemberAdminService memberAdminService;

    /** 목록 (MB-07) — 기업 관리자만. 비활성 구성원도 함께 나온다. */
    @GetMapping
    public PageResponse<MemberResponse> list(
            AccessContext ctx,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return memberAdminService.list(ctx, pageable(page, size));
    }

    /** 담당자 선택지 (DL-04) — 전 구성원. 활성만 나오고 페이징하지 않는다. */
    @GetMapping("/options")
    public List<MemberOptionResponse> options(AccessContext ctx) {
        return memberAdminService.options(ctx);
    }

    /** 역할 변경 (MB-08) — 기업 관리자만. 마지막 활성 관리자는 강등할 수 없다 (MB-11). */
    @PatchMapping("/{memberId}/role")
    public MemberResponse changeRole(AccessContext ctx, @PathVariable UUID memberId,
                                     @Valid @RequestBody ChangeRoleRequest request) {
        return memberAdminService.changeRole(ctx, memberId, request);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
    }
}
