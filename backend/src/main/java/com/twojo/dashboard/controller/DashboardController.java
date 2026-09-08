package com.twojo.dashboard.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.dashboard.dto.DashboardPerformanceResponse;
import com.twojo.dashboard.dto.DashboardSummaryResponse;
import com.twojo.dashboard.service.DashboardService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현황 대시보드 (07 §D · DB-01~08). 인증된 구성원 경로 — {@link AccessContext}는 인증 필터가
 * 심은 principal에서 주입된다 (11 §1.4). 스코프·역할 판정은 서비스가 한다.
 *
 * <p>기본값은 서버 시간(KST) 기준이다 — 대시보드는 진입하자마자 당월·이달을 보여준다.
 * 잘못된 {@code month}·{@code from}·{@code to}는 스프링 바인딩 실패로 이어져
 * {@code GlobalExceptionHandler}가 400 {@code VALIDATION_FAILED}로 처리한다.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DashboardService dashboardService;

    /** 월간 요약 (DB-01~05). {@code month} 미지정 시 당월. */
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            AccessContext ctx,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        YearMonth target = month != null ? month : YearMonth.now(KST);
        return dashboardService.summary(ctx, target);
    }

    /**
     * 실적 분석 (DB-06~08) — 기업 관리자 전용(서비스가 403으로 가른다).
     * {@code from} 미지정 시 이달 1일, {@code to} 미지정 시 오늘.
     */
    @GetMapping("/performance")
    public DashboardPerformanceResponse performance(
            AccessContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now(KST);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        return dashboardService.performance(ctx, start, end);
    }
}
