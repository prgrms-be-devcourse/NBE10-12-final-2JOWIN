package com.twojo.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.activity.entity.Activity;
import com.twojo.activity.repository.ActivityRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.ActivityQuery;
import com.twojo.boundary.ActivityQuery.RecentActivitySummary;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 대시보드 최근 활동 (DB-04) — 범위 분기가 핵심이다.
 *
 * <p>기업 관리자는 회사 전체(SC-05), 영업 담당자는 본인 담당 Deal의 활동만 본다(SC-02·04).
 * 정렬과 회사 스코프는 파생 쿼리 이름이 만드는 SQL의 몫이라 목으로는 확인되지 않는다 —
 * 로컬 실행으로 따로 확인한다 (이슈 #158 설계 결정 5·6).
 */
@ExtendWith(MockitoExtension.class)
class ActivityQueryImplTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();

    private static final AccessContext ADMIN =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private ActivityRepository activityRepository;
    @Mock private DealQuery dealQuery;
    @InjectMocks private ActivityQueryImpl activityQuery;

    private static Activity 활동(String content) {
        return Activity.create(COMPANY_ID, DEAL_ID, MEMBER_ID,
                Activity.Channel.CALL, content, Instant.parse("2026-08-25T02:00:00Z"));
    }

    @Test
    @DisplayName("기업 관리자는 회사 전체 활동을 받는다 — 담당 딜을 묻지 않는다 (SC-05)")
    void recent_admin_seesWholeCompany() {
        given(activityRepository.findByCompanyIdAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
                eq(COMPANY_ID), any()))
                .willReturn(List.of(활동("리모델링 일정 확인"), 활동("정기납품 물량 협의")));

        List<RecentActivitySummary> result = activityQuery.recent(ADMIN, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).summary()).isEqualTo("리모델링 일정 확인");
        then(dealQuery).should(never()).assignedDealIds(any(), any());
    }

    @Test
    @DisplayName("영업 담당자는 본인 담당 Deal의 활동만 받는다 (SC-02·04)")
    void recent_salesRep_filteredByAssignedDeals() {
        List<UUID> 담당딜 = List.of(DEAL_ID);
        given(dealQuery.assignedDealIds(COMPANY_ID, MEMBER_ID)).willReturn(담당딜);
        given(activityRepository.findByCompanyIdAndDealIdInAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
                eq(COMPANY_ID), eq(담당딜), any()))
                .willReturn(List.of(활동("리모델링 일정 확인")));

        List<RecentActivitySummary> result = activityQuery.recent(SALES, 10);

        assertThat(result).hasSize(1);
        then(activityRepository).should(never())
                .findByCompanyIdAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(any(), any());
    }

    @Test
    @DisplayName("담당 Deal이 없으면 조회하지 않고 빈 목록을 돌려준다 — 빈 IN 절을 만들지 않는다")
    void recent_salesRepWithNoDeals_skipsQuery() {
        given(dealQuery.assignedDealIds(COMPANY_ID, MEMBER_ID)).willReturn(List.of());

        List<RecentActivitySummary> result = activityQuery.recent(SALES, 10);

        then(activityRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("limit이 1~MAX_LIMIT 밖이면 IllegalArgumentException — 호출부가 정하는 상수라 잘라내지 않는다")
    void recent_limitOutOfRange_throws() {
        assertThatThrownBy(() -> activityQuery.recent(ADMIN, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> activityQuery.recent(ADMIN, ActivityQuery.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);

        then(activityRepository).shouldHaveNoInteractions();
        then(dealQuery).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("내용은 80자까지 그대로, 넘으면 80자에서 자르고 …를 붙인다")
    void recent_longContent_truncated() {
        String 여든자 = "가".repeat(80);
        String 여든한자 = "가".repeat(81);
        given(activityRepository.findByCompanyIdAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
                eq(COMPANY_ID), any()))
                .willReturn(List.of(활동(여든자), 활동(여든한자)));

        List<RecentActivitySummary> result = activityQuery.recent(ADMIN, 10);

        assertThat(result.get(0).summary()).isEqualTo(여든자);
        assertThat(result.get(1).summary()).isEqualTo("가".repeat(80) + "…");
    }

    @Test
    @DisplayName("이모지가 80번째 자리에 걸치면 한 칸 물려 자른다 — 반쪽만 남으면 깨진 글자가 나간다")
    void recent_emojiOnBoundary_notSplit() {
        // 79자 + 이모지(저장 단위 2칸) = 80번째 자리가 이모지의 앞쪽이다
        String content = "가".repeat(79) + "\uD83D\uDC4D";
        given(activityRepository.findByCompanyIdAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
                eq(COMPANY_ID), any()))
                .willReturn(List.of(활동(content)));

        String summary = activityQuery.recent(ADMIN, 10).get(0).summary();

        assertThat(summary).isEqualTo("가".repeat(79) + "…");
        assertThat(summary).doesNotContain("\uD83D");
    }
}
