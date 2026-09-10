package com.twojo.activity.service;

import com.twojo.activity.dto.ActivityResponse;
import com.twojo.activity.dto.CreateActivityRequest;
import com.twojo.activity.dto.UpdateActivityRequest;
import com.twojo.activity.entity.Activity;
import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.ActivityRepository;
import com.twojo.activity.repository.AuditLogRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.AuditActorType;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import com.twojo.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상담 기록·딜 타임라인 (AC-01~10).
 *
 * <p>범위 축이 Deal이다 — 상담 기록 자체에는 담당이 없고, 붙어 있는 Deal이 보이면 보인다
 * (09 §59 "담당 Deal 범위"). 영업은 본인 담당 Deal만, 기업 관리자는 회사 전체다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    /** 07의 {@code ?type=} 값. 수동은 구성원이 쓴 상담 기록, 자동은 감사 로그에서 온 줄이다. */
    private static final String TYPE_MANUAL = "MANUAL";
    private static final String TYPE_AUTO = "AUTO";

    private final ActivityRepository activityRepository;
    private final AuditLogRepository auditLogRepository;
    private final AutoActivityText autoActivityText;
    private final CustomerQuery customerQuery;
    private final DealAccess dealAccess;
    private final MemberQuery memberQuery;

    /** 상담 기록 등록 (AC-01~03) — 회사·작성자는 컨텍스트에서 채운다. 요청 바디에 없다. */
    @Transactional
    public ActivityResponse create(AccessContext ctx, UUID dealId, CreateActivityRequest request) {
        dealAccess.requireInScope(ctx, dealId);

        Activity activity = activityRepository.save(Activity.create(
                ctx.companyId(), dealId, ctx.memberId(),
                Activity.Channel.valueOf(request.channel()), request.content(), request.occurredAt()));

        return toResponse(activity);
    }

    /**
     * 딜 타임라인 (AC-06·07) — 수동 기록과 자동 기록을 시각 내림차순으로 합친다.
     *
     * <p>{@code type}이 {@code MANUAL}·{@code AUTO} 중 하나면 그쪽만, null이면 둘 다 본다.
     * 한쪽만 볼 때 다른 쪽을 조회하지 않는다 — 화면이 탭으로 나뉘어 있어 매번 두 번 읽을 이유가 없다.
     *
     * <p>{@code audit_log}는 이 모듈이 소유해 경계를 거치지 않는다. 다만 <b>병합 키가
     * {@code entity_id}가 아니라 {@code payload}의 {@code dealId}다</b> — 사유는 리포지토리 javadoc.
     *
     * <p><b>자르기는 합친 뒤에 한다</b> — 두 원천에서 각각 한 페이지씩 떠 오면 합쳤을 때 그 페이지가
     * 시간순이 아니다. 그래서 딜 하나의 이력을 전부 읽고 메모리에서 자른다. 한 딜의 이력은
     * 화면이 스크롤로 감당하는 크기라 지금 규모에서는 이 방식이 맞다.
     */
    public PageResponse<ActivityResponse> timeline(AccessContext ctx, UUID dealId, String type,
                                                   Pageable pageable) {
        requireKnownType(type);
        dealAccess.requireInScope(ctx, dealId);

        Stream<ActivityResponse> manual = TYPE_AUTO.equals(type) ? Stream.empty()
                : activityRepository
                        .findByCompanyIdAndDealIdAndDeletedAtIsNullOrderByOccurredAtDesc(
                                ctx.companyId(), dealId)
                        .stream().map(this::toResponse);

        Stream<ActivityResponse> auto = TYPE_MANUAL.equals(type) ? Stream.empty()
                : auditLogRepository.findByDealId(ctx.companyId(), dealId)
                        .stream().map(this::toResponse);

        return page(Stream.concat(manual, auto)
                .sorted(Comparator.comparing(ActivityResponse::occurredAt).reversed())
                .toList(), pageable);
    }

    /**
     * 고객사 이력 (AC-10) — 경로는 고객사지만 조회 대상은 상담 기록이다.
     *
     * <p>고객사는 회사 공유 자원이라 <b>존재 확인만</b> 하면 되고(타사 것은 그 창구가 404를 낸다),
     * 그 뒤 범위는 상담 기록의 규칙을 따른다 — 영업은 <b>담당 Deal의 상담만</b> 본다 (09 §59).
     * 같은 고객사라도 남이 담당하는 Deal의 상담은 보이지 않는다.
     *
     */
    public PageResponse<ActivityResponse> byCustomer(AccessContext ctx, UUID customerId, Pageable pageable) {
        customerQuery.get(ctx, customerId);

        List<UUID> dealIds = dealAccess.dealIdsOfCustomer(customerId);
        if (ctx.scope() == AccessScope.OWNED_ONLY) {
            Set<UUID> assigned = Set.copyOf(dealAccess.assignedDealIds(ctx));
            dealIds = dealIds.stream().filter(assigned::contains).toList();
        }
        if (dealIds.isEmpty()) {
            // 빈 IN 절은 처리 방식이 환경에 따라 다르다. 조회하지 않는다
            return PageResponse.from(Page.<ActivityResponse>empty(pageable));
        }

        return page(activityRepository
                .findByCompanyIdAndDealIdInAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
                        ctx.companyId(), dealIds, Pageable.unpaged())
                .stream().map(this::toResponse).toList(), pageable);
    }

    /**
     * 이미 정렬된 목록을 한 페이지로 자른다 (Q-39 · 08 §0 "목록은 공통 PageResponse").
     *
     * <p>리포지토리 페이징을 쓸 수 없는 자리에서만 쓴다 — 딜 타임라인은 두 테이블을 합친 뒤에
     * 잘라야 하고, 고객사 이력은 딜 id 목록이 정해진 뒤에야 조회가 성립한다.
     */
    private static PageResponse<ActivityResponse> page(List<ActivityResponse> rows, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return PageResponse.from(new PageImpl<>(rows.subList(from, to), pageable, rows.size()));
    }

    /**
     * 상담 기록 수정 (AC-04) — <b>작성자 본인만</b>. null 필드는 바꾸지 않는다.
     *
     * <p>Deal 범위를 다시 묻지 않는다 — 작성자 판정이 더 좁다. 본인이 쓴 기록은 본인이 담당했던
     * Deal의 것이고, 담당이 이관돼도 그 기록은 여전히 본인 것이다 (AC-08).
     */
    @Transactional
    public ActivityResponse update(AccessContext ctx, UUID activityId, UpdateActivityRequest request) {
        Activity activity = findByAuthor(ctx, activityId);
        activity.update(channelOrNull(request.channel()), request.content(), request.occurredAt());
        return toResponse(activity);
    }

    /**
     * 상담 기록 삭제 (AC-05) — <b>작성자 본인만</b>. 소프트 삭제라 행은 남는다.
     *
     * <p>행을 지우지 않는 이유는 AC-08이다 — 담당이 바뀌어도 이력이 남아야 하고, 자동 기록과
     * 함께 놓이는 타임라인에서 과거를 지우면 그 시점의 사실이 사라진다.
     */
    @Transactional
    public void delete(AccessContext ctx, UUID activityId, Instant now) {
        findByAuthor(ctx, activityId).softDelete(now);
    }

    /**
     * 회사 스코프 안에서 찾고 작성자까지 확인한다 — 둘 다 404다 (SC-09).
     *
     * <p><b>기업 관리자도 예외가 없다</b>. 판정 축이 역할이 아니라 {@code author_member_id}라
     * 타인 기록은 누구도 손대지 못한다 (09 §60).
     */
    private Activity findByAuthor(AccessContext ctx, UUID activityId) {
        Activity activity = activityRepository
                .findByIdAndCompanyIdAndDeletedAtIsNull(activityId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!ctx.memberId().equals(activity.getAuthorMemberId())) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_AUTHOR);
        }
        return activity;
    }

    private static Activity.Channel channelOrNull(String channel) {
        return channel == null ? null : Activity.Channel.valueOf(channel);
    }

    /**
     * 07에 없는 {@code type}은 400이다 — 오타를 전체 조회로 흘리면 필터가 걸린 줄 알고 본다.
     * 07 부록에 전용 에러가 없어 {@code VALIDATION_FAILED}에 어느 필드인지를 실어 보낸다.
     */
    private static void requireKnownType(String type) {
        if (type != null && !TYPE_MANUAL.equals(type) && !TYPE_AUTO.equals(type)) {
            throw BusinessException.invalidField(
                    "type", TYPE_MANUAL + " · " + TYPE_AUTO + " 중 하나여야 합니다");
        }
    }

    /**
     * 자동 기록을 타임라인 한 줄로 (AC-07).
     *
     * <p>{@code channel}이 없다 — 상담 수단은 사람이 고르는 값이고 자동 기록에는 그런 선택이 없다.
     *
     * <p>{@code content}에는 <b>표시 문장</b>이 들어간다 (10 §5.3 목업 "견적을 발송했습니다 — Q-…").
     * 08 {@code ActivityResponse}에 이벤트 종류 필드가 없어 화면이 코드로 문장을 만들 수 없다.
     *
     * <p>작성자는 {@code MEMBER}일 때만 있다. {@code SYSTEM}·{@code CUSTOMER_LINK}는 계정이 없어
     * 물을 곳이 없다 — 그때 {@code authorActive}는 <b>true</b>다. 비활성 표시는 사람에 대한
     * 것이라 사람이 없는 줄에 "(퇴사)"가 붙으면 안 된다.
     */
    private ActivityResponse toResponse(AuditLog auditLog) {
        UUID actorId = auditLog.getActorType() == AuditActorType.MEMBER ? auditLog.getActorId() : null;
        MemberQuery.MemberSummary actor = actorId == null ? null : memberQuery.get(actorId);
        return new ActivityResponse(
                auditLog.getId(), TYPE_AUTO, null,
                autoActivityText.of(auditLog.getEventType(), auditLog.getPayload(), auditLog.getId()),
                actorId,
                actor == null ? null : actor.name(),
                actor == null || actor.active(),
                auditLog.getOccurredAt());
    }

    /** 작성자 이름·활성 여부는 표시용이다 — 비활성 작성자의 기록도 그대로 남는다 (AC-08). */
    private ActivityResponse toResponse(Activity activity) {
        MemberQuery.MemberSummary author = memberQuery.get(activity.getAuthorMemberId());
        return new ActivityResponse(
                activity.getId(), TYPE_MANUAL, activity.getChannel().name(), activity.getContent(),
                activity.getAuthorMemberId(), author.name(), author.active(), activity.getOccurredAt());
    }
}
