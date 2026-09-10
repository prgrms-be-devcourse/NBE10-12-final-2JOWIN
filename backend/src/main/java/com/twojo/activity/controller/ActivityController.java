package com.twojo.activity.controller;

import com.twojo.activity.dto.ActivityResponse;
import com.twojo.activity.dto.CreateActivityRequest;
import com.twojo.activity.dto.UpdateActivityRequest;
import com.twojo.activity.service.ActivityService;
import com.twojo.boundary.AccessContext;
import com.twojo.global.response.PageResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상담 기록·타임라인 (07 §B · AC-01~10).
 *
 * <p>경로 앞머리가 셋이라({@code deals}·{@code activities}·{@code customers}) 클래스 매핑을
 * {@code /api/v1}로 두고 메서드마다 나머지를 적는다 — 주문과 같은 형태다.
 *
 * <p>범위·작성자 판정은 컨트롤러가 아니라 서비스가 한다. 여기서 막으면 다른 호출 경로가
 * 생겼을 때 그대로 뚫린다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ActivityController {

    /** Q-39 — 목록 응답은 공통 PageResponse 다 (08 §0). */
    private static final int MAX_PAGE_SIZE = 100;

    private final ActivityService activityService;

    /** 상담 기록 등록 (AC-01~03) — 작성자는 서버가 채운다. 요청 바디에 없다. */
    @PostMapping("/deals/{dealId}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse create(AccessContext ctx, @PathVariable UUID dealId,
                                   @Valid @RequestBody CreateActivityRequest request) {
        return activityService.create(ctx, dealId, request);
    }

    /**
     * 딜 타임라인 (AC-06·07) — 수동 기록과 자동 기록을 합쳐 시간 역순으로 돌려준다.
     * {@code type}을 안 보내면 둘 다 나온다.
     */
    @GetMapping("/deals/{dealId}/activities")
    public PageResponse<ActivityResponse> timeline(
            AccessContext ctx, @PathVariable UUID dealId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return activityService.timeline(ctx, dealId, type, pageable(page, size));
    }

    /** 수정 (AC-04) — 작성자 본인만. null 필드는 미변경 */
    @PatchMapping("/activities/{activityId}")
    public ActivityResponse update(AccessContext ctx, @PathVariable UUID activityId,
                                   @Valid @RequestBody UpdateActivityRequest request) {
        return activityService.update(ctx, activityId, request);
    }

    /** 삭제 (AC-05) — 작성자 본인만. 소프트 삭제라 타임라인의 과거는 남는다 (AC-08) */
    @DeleteMapping("/activities/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(AccessContext ctx, @PathVariable UUID activityId) {
        activityService.delete(ctx, activityId, Instant.now());
    }

    /** 고객사 이력 (AC-10) — 경로는 고객사지만 조회 대상은 상담 기록이다 */
    @GetMapping("/customers/{customerId}/activities")
    public PageResponse<ActivityResponse> byCustomer(
            AccessContext ctx, @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return activityService.byCustomer(ctx, customerId, pageable(page, size));
    }

    /**
     * Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭).
     *
     * <p>정렬은 넘기지 않는다. 두 목록 다 서비스가 시각 역순으로 이미 맞춰 돌려주고,
     * 타임라인은 두 테이블을 합친 뒤에 자르므로 {@code Sort}가 조회에 닿지 않는다.
     */
    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }
}
