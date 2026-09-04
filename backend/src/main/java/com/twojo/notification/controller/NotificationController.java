package com.twojo.notification.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.global.response.PageResponse;
import com.twojo.notification.dto.NotificationResponse;
import com.twojo.notification.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인앱 알림 (07 §D · NT-08) — 폴링 30초.
 *
 * <p>{@link AccessContext}는 인증 필터가 심은 principal에서 주입된다 — 요청에 회사·구성원 식별자가 없다.
 * 스코프·존재 판정은 서비스가 한다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 최신순 고정 (Q-39 "정렬은 엔드포인트별 기본값"). {@code id}를 tie-breaker로 둔다 —
     * 배치가 한 트랜잭션에 여러 행을 쓰면 {@code created_at}이 동률이라 페이지 경계가 흔들린다.
     */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final NotificationService notificationService;

    /** 목록 (본인 수신분). {@code unreadOnly=true}면 안 읽은 것만 — 이때 totalElements가 배지 수. */
    @GetMapping
    public PageResponse<NotificationResponse> list(
            AccessContext ctx,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(ctx, unreadOnly, pageable(page, size));
    }

    /** 1건 읽음 (NT-08) — 남의 것이면 404, 이미 읽음이면 멱등. */
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(AccessContext ctx, @PathVariable UUID id) {
        notificationService.markRead(ctx, id);
    }

    /** 모두 읽음 (NT-08) — 미읽음이 없으면 그대로 204. */
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(AccessContext ctx) {
        notificationService.markAllRead(ctx);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
    }
}
