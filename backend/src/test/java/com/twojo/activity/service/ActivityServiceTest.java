package com.twojo.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.twojo.activity.dto.ActivityResponse;
import com.twojo.activity.dto.CreateActivityRequest;
import com.twojo.activity.dto.UpdateActivityRequest;
import com.twojo.activity.entity.Activity;
import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.ActivityRepository;
import com.twojo.activity.repository.AuditLogRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.AuditActor;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 상담 기록·타임라인 (AC-01~10) — 범위 판정과 작성자 판정의 검증.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();

    private static final Pageable PAGE = PageRequest.of(0, 20);

    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private ActivityRepository activityRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AutoActivityText autoActivityText;
    @Mock private CustomerQuery customerQuery;
    @Mock private DealAccess dealAccess;
    @Mock private MemberQuery memberQuery;
    @InjectMocks private ActivityService activityService;

    @Test
    @DisplayName("담당 Deal이면 상담 기록을 만들고 회사·작성자를 컨텍스트에서 채운다")
    void create_fillsCompanyAndAuthorFromContext() {
        Instant occurredAt = Instant.parse("2026-09-09T02:00:00Z");
        given(memberQuery.get(MEMBER_ID))
                .willReturn(new MemberQuery.MemberSummary(MEMBER_ID, "한상민", true));
        given(activityRepository.save(any(Activity.class))).willAnswer(call -> call.getArgument(0));

        var response = activityService.create(SALES, DEAL_ID,
                new CreateActivityRequest("CALL", "예산 확인", occurredAt));

        assertThat(response.type()).isEqualTo("MANUAL");
        assertThat(response.channel()).isEqualTo("CALL");
        assertThat(response.content()).isEqualTo("예산 확인");
        assertThat(response.authorMemberId()).isEqualTo(MEMBER_ID);
        assertThat(response.authorMemberName()).isEqualTo("한상민");
        assertThat(response.authorActive()).isTrue();
        assertThat(response.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("작성자 본인이면 보낸 필드만 바꾼다 — null은 미변경")
    void update_changesOnlyGivenFields() {
        Activity activity = Activity.create(COMPANY_ID, DEAL_ID, MEMBER_ID,
                Activity.Channel.CALL, "예산 확인", Instant.parse("2026-09-09T02:00:00Z"));
        given(activityRepository.findByIdAndCompanyIdAndDeletedAtIsNull(ACTIVITY_ID, COMPANY_ID))
                .willReturn(Optional.of(activity));
        given(memberQuery.get(MEMBER_ID))
                .willReturn(new MemberQuery.MemberSummary(MEMBER_ID, "한상민", true));

        var response = activityService.update(SALES, ACTIVITY_ID,
                new UpdateActivityRequest("MEETING", null, null));

        assertThat(response.channel()).isEqualTo("MEETING");
        assertThat(response.content()).isEqualTo("예산 확인");
        assertThat(response.occurredAt()).isEqualTo(Instant.parse("2026-09-09T02:00:00Z"));
    }

    /** 관리자도 예외가 없다 — 판정 축이 역할이 아니라 {@code author_member_id}다 (09 §60). */
    @Test
    @DisplayName("타인이 쓴 기록은 수정할 수 없다 — ACTIVITY_NOT_AUTHOR (404)")
    void update_notAuthor_throwsNotAuthor() {
        Activity activity = Activity.create(COMPANY_ID, DEAL_ID, UUID.randomUUID(),
                Activity.Channel.CALL, "예산 확인", Instant.parse("2026-09-09T02:00:00Z"));
        given(activityRepository.findByIdAndCompanyIdAndDeletedAtIsNull(ACTIVITY_ID, COMPANY_ID))
                .willReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.update(SALES, ACTIVITY_ID,
                new UpdateActivityRequest("MEETING", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_NOT_AUTHOR);
    }

    @Test
    @DisplayName("작성자 본인이면 소프트 삭제한다 — 행은 남고 deletedAt이 찍힌다")
    void delete_softDeletes() {
        Activity activity = Activity.create(COMPANY_ID, DEAL_ID, MEMBER_ID,
                Activity.Channel.CALL, "예산 확인", Instant.parse("2026-09-09T02:00:00Z"));
        given(activityRepository.findByIdAndCompanyIdAndDeletedAtIsNull(ACTIVITY_ID, COMPANY_ID))
                .willReturn(Optional.of(activity));

        activityService.delete(SALES, ACTIVITY_ID, Instant.parse("2026-09-10T01:00:00Z"));

        assertThat(activity.getDeletedAt()).isEqualTo(Instant.parse("2026-09-10T01:00:00Z"));
    }

    @Test
    @DisplayName("타인이 쓴 기록은 삭제할 수 없다 — ACTIVITY_NOT_AUTHOR (404)")
    void delete_notAuthor_throwsNotAuthor() {
        Activity activity = Activity.create(COMPANY_ID, DEAL_ID, UUID.randomUUID(),
                Activity.Channel.CALL, "예산 확인", Instant.parse("2026-09-09T02:00:00Z"));
        given(activityRepository.findByIdAndCompanyIdAndDeletedAtIsNull(ACTIVITY_ID, COMPANY_ID))
                .willReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.delete(SALES, ACTIVITY_ID, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_NOT_AUTHOR);
    }

    @Test
    @DisplayName("타임라인은 수동 기록과 자동 기록을 시각 내림차순으로 합친다")
    void timeline_mergesManualAndAutoByOccurredAt() {
        given(activityRepository.findByCompanyIdAndDealIdAndDeletedAtIsNullOrderByOccurredAtDesc(
                COMPANY_ID, DEAL_ID))
                .willReturn(List.of(activity(MEMBER_ID, "예산 확인", "2026-09-09T05:00:00Z")));
        given(auditLogRepository.findByDealId(COMPANY_ID, DEAL_ID))
                .willReturn(List.of(auditLog(AuditActor.system(), "2026-09-09T07:00:00Z")));
        given(autoActivityText.of(eq("STAGE_MOVED"), any(), any()))
                .willReturn("단계를 이동했습니다 — CONSULT → QUOTE");
        given(memberQuery.get(MEMBER_ID))
                .willReturn(new MemberQuery.MemberSummary(MEMBER_ID, "한상민", true));

        var timeline = activityService.timeline(SALES, DEAL_ID, null, PAGE);

        assertThat(timeline.content()).extracting(ActivityResponse::type).containsExactly("AUTO", "MANUAL");
        assertThat(timeline.content().getFirst().channel()).isNull();
        assertThat(timeline.content().getFirst().content()).isEqualTo("단계를 이동했습니다 — CONSULT → QUOTE");
        assertThat(timeline.content().getFirst().authorMemberId()).isNull();
        assertThat(timeline.content().getFirst().authorActive())
                .as("사람이 없는 줄에 (퇴사)가 붙으면 안 된다").isTrue();
        assertThat(timeline.content().getLast().authorMemberName()).isEqualTo("한상민");
    }

    /**
     * 두 원천에서 각각 한 페이지씩 떠 오면 합쳤을 때 시간순이 깨진다 — 합친 뒤에 잘라야 한다.
     * 여기서는 자동 기록이 더 최신이라 1페이지에 그것만 와야 정상이다.
     */
    @Test
    @DisplayName("페이지는 두 원천을 합쳐 정렬한 뒤에 자른다")
    void timeline_pagesAfterMerge() {
        given(activityRepository.findByCompanyIdAndDealIdAndDeletedAtIsNullOrderByOccurredAtDesc(
                COMPANY_ID, DEAL_ID))
                .willReturn(List.of(activity(MEMBER_ID, "예산 확인", "2026-09-09T05:00:00Z")));
        given(auditLogRepository.findByDealId(COMPANY_ID, DEAL_ID))
                .willReturn(List.of(auditLog(AuditActor.system(), "2026-09-09T07:00:00Z")));
        given(autoActivityText.of(eq("STAGE_MOVED"), any(), any())).willReturn("단계를 이동했습니다");
        given(memberQuery.get(MEMBER_ID))
                .willReturn(new MemberQuery.MemberSummary(MEMBER_ID, "한상민", true));

        var first = activityService.timeline(SALES, DEAL_ID, null, PageRequest.of(0, 1));

        assertThat(first.content()).extracting(ActivityResponse::type).containsExactly("AUTO");
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("마지막 페이지를 넘어선 요청은 빈 목록이다 — 총 건수는 그대로 센다")
    void timeline_pastLastPage_isEmpty() {
        given(activityRepository.findByCompanyIdAndDealIdAndDeletedAtIsNullOrderByOccurredAtDesc(
                COMPANY_ID, DEAL_ID))
                .willReturn(List.of(activity(MEMBER_ID, "예산 확인", "2026-09-09T05:00:00Z")));
        given(memberQuery.get(MEMBER_ID))
                .willReturn(new MemberQuery.MemberSummary(MEMBER_ID, "한상민", true));

        var page = activityService.timeline(SALES, DEAL_ID, "MANUAL", PageRequest.of(5, 20));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("type=AUTO면 수동 기록은 조회하지 않는다")
    void timeline_autoOnly_skipsActivity() {
        given(auditLogRepository.findByDealId(COMPANY_ID, DEAL_ID))
                .willReturn(List.of(auditLog(AuditActor.system(), "2026-09-09T07:00:00Z")));
        given(autoActivityText.of(eq("STAGE_MOVED"), any(), any())).willReturn("단계를 이동했습니다");

        var timeline = activityService.timeline(SALES, DEAL_ID, "AUTO", PAGE);

        assertThat(timeline.content()).extracting(ActivityResponse::type).containsExactly("AUTO");
        then(activityRepository).shouldHaveNoInteractions();
    }

    /** 오타를 전체 조회로 흘리면 필터가 걸린 줄 알고 본다 — 07에 없는 값은 막는다. */
    @Test
    @DisplayName("모르는 type은 조회 전에 400으로 막는다")
    void timeline_unknownType_throwsValidationFailed() {
        assertThatThrownBy(() -> activityService.timeline(SALES, DEAL_ID, "auto", PAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        then(dealAccess).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기업 관리자의 고객사 이력은 담당을 묻지 않고 그 고객사 Deal 전부를 본다")
    void byCustomer_admin_doesNotFilterByAssignee() {
        AccessContext admin = new AccessContext(
                COMPANY_ID, UUID.randomUUID(), Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
        UUID customerId = UUID.randomUUID();
        UUID othersDealId = UUID.randomUUID();
        given(customerQuery.get(admin, customerId))
                .willReturn(new CustomerQuery.CustomerSummary(customerId, "도담건설"));
        given(dealAccess.dealIdsOfCustomer(customerId)).willReturn(List.of(DEAL_ID, othersDealId));
        given(activityRepository.findByCompanyIdAndDealIdInAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
                COMPANY_ID, List.of(DEAL_ID, othersDealId), Pageable.unpaged()))
                .willReturn(List.of(activity(MEMBER_ID, "예산 확인", "2026-09-09T05:00:00Z")));
        given(memberQuery.get(MEMBER_ID))
                .willReturn(new MemberQuery.MemberSummary(MEMBER_ID, "한상민", true));

        assertThat(activityService.byCustomer(admin, customerId, PAGE).content()).hasSize(1);
    }

    @Test
    @DisplayName("type=MANUAL이면 자동 기록은 조회하지 않는다")
    void timeline_manualOnly_skipsAuditLog() {
        given(activityRepository.findByCompanyIdAndDealIdAndDeletedAtIsNullOrderByOccurredAtDesc(
                COMPANY_ID, DEAL_ID))
                .willReturn(List.of(activity(MEMBER_ID, "예산 확인", "2026-09-09T05:00:00Z")));
        given(memberQuery.get(MEMBER_ID))
                .willReturn(new MemberQuery.MemberSummary(MEMBER_ID, "한상민", true));

        var timeline = activityService.timeline(SALES, DEAL_ID, "MANUAL", PAGE);

        assertThat(timeline.content()).extracting(ActivityResponse::type).containsExactly("MANUAL");
        then(auditLogRepository).shouldHaveNoInteractions();
    }

    /**
     * 고객사는 회사 공유 자원이지만 상담 기록은 <b>담당 Deal 범위</b>다 (09 §60).
     * 그래서 같은 고객사라도 남이 담당하는 Deal의 상담은 영업에게 보이지 않는다.
     */
    @Test
    @DisplayName("고객사 이력은 그 고객사 Deal 중 담당분의 상담 기록만 모은다 (AC-10)")
    void byCustomer_collectsOwnedDealsOnly() {
        UUID customerId = UUID.randomUUID();
        UUID othersDealId = UUID.randomUUID();
        given(customerQuery.get(SALES, customerId))
                .willReturn(new CustomerQuery.CustomerSummary(customerId, "도담건설"));
        given(dealAccess.dealIdsOfCustomer(customerId)).willReturn(List.of(DEAL_ID, othersDealId));
        given(dealAccess.assignedDealIds(SALES)).willReturn(List.of(DEAL_ID));
        given(activityRepository.findByCompanyIdAndDealIdInAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
                COMPANY_ID, List.of(DEAL_ID), Pageable.unpaged()))
                .willReturn(List.of(activity(MEMBER_ID, "예산 확인", "2026-09-09T05:00:00Z")));
        given(memberQuery.get(MEMBER_ID))
                .willReturn(new MemberQuery.MemberSummary(MEMBER_ID, "한상민", true));

        var history = activityService.byCustomer(SALES, customerId, PAGE);

        assertThat(history.content()).extracting(ActivityResponse::content).containsExactly("예산 확인");
    }

    @Test
    @DisplayName("볼 수 있는 Deal이 없으면 조회하지 않고 빈 목록을 돌려준다")
    void byCustomer_noVisibleDeals_returnsEmpty() {
        UUID customerId = UUID.randomUUID();
        given(customerQuery.get(SALES, customerId))
                .willReturn(new CustomerQuery.CustomerSummary(customerId, "도담건설"));
        given(dealAccess.dealIdsOfCustomer(customerId)).willReturn(List.of());

        assertThat(activityService.byCustomer(SALES, customerId, PAGE).content()).isEmpty();
        then(activityRepository).shouldHaveNoInteractions();
    }

    private static Activity activity(UUID authorId, String content, String occurredAt) {
        return Activity.create(COMPANY_ID, DEAL_ID, authorId,
                Activity.Channel.CALL, content, Instant.parse(occurredAt));
    }

    private static AuditLog auditLog(AuditActor actor, String occurredAt) {
        return AuditLog.of(COMPANY_ID, "DEAL", DEAL_ID, "STAGE_MOVED",
                actor, Instant.parse(occurredAt), "{\"dealId\":\"" + DEAL_ID + "\"}");
    }

    private static CreateActivityRequest request() {
        return new CreateActivityRequest("CALL", "예산 확인", Instant.parse("2026-09-09T02:00:00Z"));
    }


}
