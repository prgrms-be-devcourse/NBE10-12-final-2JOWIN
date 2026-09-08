package com.twojo.activity.service;

import com.twojo.activity.entity.Activity;
import com.twojo.activity.repository.ActivityRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.ActivityQuery;
import com.twojo.boundary.DealQuery;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 최근 활동 조회 (DB-04) — {@link ActivityQuery} 구현.
 *
 * <p>딜 제목은 여기서 붙이지 않는다 — 소비자가 조립한다 (11 §7.2).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ActivityQueryImpl implements ActivityQuery {

    /** 카드 한 줄 상한. 시드 활동 최장 30자 기준 두 배 이상 여유 — 정상 입력은 잘리지 않는다 */
    private static final int SUMMARY_MAX = 80;

    private final ActivityRepository activityRepository;
    private final DealQuery dealQuery;

    @Override
    public List<RecentActivitySummary> recent(AccessContext ctx, int limit) {
        requireValidLimit(limit);

        Pageable page = PageRequest.of(0, limit);
        List<Activity> rows;

        if (ctx.scope() == AccessScope.COMPANY_ALL) {
            // 관리자는 담당 딜을 묻지 않는다 — 회사 전체 딜 id를 받아 IN 절에 넣는 것이 답이 아니다
            rows = activityRepository.findByCompanyIdAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
                    ctx.companyId(), page);
        } else {
            List<UUID> dealIds = dealQuery.assignedDealIds(ctx.companyId(), ctx.memberId());
            if (dealIds.isEmpty()) {
                // 빈 IN 절은 처리 방식이 환경에 따라 다르다. 조회하지 않는다
                return List.of();
            }
            rows = activityRepository
                    .findByCompanyIdAndDealIdInAndDeletedAtIsNullOrderByOccurredAtDescIdAsc(
                            ctx.companyId(), dealIds, page);
        }

        return rows.stream()
                .map(a -> new RecentActivitySummary(a.getDealId(), toSummary(a.getContent()), a.getOccurredAt()))
                .toList();
    }

    /**
     * 카드 한 줄에 실을 표시용 문자열 (이슈 #158 설계 결정 2).
     *
     * <p>{@code activity.content}는 {@code TEXT}라 길이 상한이 없다. 대시보드 카드에는 자르는
     * CSS가 없어 긴 내용이 그대로 그려지므로 여기서 막는다.
     *
     * <p>뜻을 보지 않는다 — 어미 정리나 중요 문장 선택은 하지 않고 길이만 센다.
     * 줄바꿈과 연속 공백은 공백 하나로 줄인다(카드가 한 줄 구조다).
     * 이모지가 경계에 걸치면 한 칸 물린다 — {@code substring}은 글자가 아니라 저장 단위를 세기 때문이다.
     */
    private static String toSummary(String content) {
        String flat = content.replaceAll("\\s+", " ").trim();
        if (flat.length() <= SUMMARY_MAX) {
            return flat;
        }
        int end = SUMMARY_MAX;
        if (Character.isHighSurrogate(flat.charAt(end - 1))) {
            end--;
        }
        return flat.substring(0, end) + "…";
    }

    /**
     * 계약이 정한 범위 밖이면 프로그래밍 오류로 드러낸다 — 잘라내 넘기지 않는다.
     * 이 값은 사용자 입력이 아니라 호출부가 정하는 상수다 (ActivityQuery javadoc).
     */
    private static void requireValidLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit은 1 이상 " + MAX_LIMIT + " 이하여야 한다: " + limit);
        }
    }
}
