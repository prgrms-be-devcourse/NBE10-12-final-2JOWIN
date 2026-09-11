package com.twojo.activity.controller;

import com.twojo.activity.dto.AuditLogDetailResponse;
import com.twojo.activity.dto.AuditLogResponse;
import com.twojo.activity.service.AuditLogService;
import com.twojo.boundary.AccessContext;
import com.twojo.global.response.PageResponse;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 로그 조회 (07 §B · AC-11) — 기업 관리자 전용이다.
 *
 * <p>역할 판정은 컨트롤러가 아니라 서비스가 한다. 여기서 막으면 다른 호출 경로가 생겼을 때
 * 그대로 뚫린다 — 사유는 {@code AuditLogService} javadoc.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int MAX_PAGE_SIZE = 100;

    /** 최근 사건이 위다 — 감사 화면은 방금 무슨 일이 있었는지부터 본다 (Q-39, 엔드포인트별 고정). */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "occurredAt");

    private final AuditLogService auditLogService;

    /**
     * 목록 (AC-11) — payload는 싣지 않는다. 세 파라미터는 전부 선택이고 안 보내면 조건에서 빠진다.
     * 기간은 사건이 일어난 시각 기준이고, {@code from}·{@code to}는 한국 날짜({@code yyyy-MM-dd})다 —
     * {@code to}는 그날을 포함한다 (07 §B, #289).
     */
    @GetMapping
    public PageResponse<AuditLogResponse> list(
            AccessContext ctx,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditLogService.list(ctx, entityType, from, to, pageable(page, size));
    }

    /** 상세 (AC-11) — payload를 changes로 펼친다. 없거나 타사 것이면 404다 (SC-09). */
    @GetMapping("/{auditLogId}")
    public AuditLogDetailResponse get(AccessContext ctx, @PathVariable UUID auditLogId) {
        return auditLogService.get(ctx, auditLogId);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
    }
}
