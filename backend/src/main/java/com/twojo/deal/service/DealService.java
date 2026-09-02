package com.twojo.deal.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.deal.dto.DealRequests;
import com.twojo.deal.dto.DealResponses;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deal 생성·조회·수정 (DL-01~06·13~15).
 *
 * <p><b>범위 판정은 {@code ctx.scope()}만 읽는다</b> — Role→Scope 변환은 인증 필터가 이미 했다
 * (PR #30). 양쪽에서 판정하면 규칙이 바뀔 때 한쪽만 고쳐진다.
 *
 * <p>타 도메인 참조는 전부 경계 인터페이스 경유다 (11 §7.3) — 고객사는 {@link CustomerQuery},
 * 구성원은 {@link MemberQuery}. 참조 ID가 같은 회사 소속이 아니면 403이 아니라
 * <b>404</b>다 (SC-09, 검증 노트 #3).
 *
 * <p>단계 전이(advance·revert·lose·reopen)는 이 클래스에 없다 — 전이표 §5를 엔티티 메서드로
 * 옮기는 작업이라 별도 이슈로 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealService {

    private final DealRepository dealRepository;
    private final CustomerQuery customerQuery;
    private final MemberQuery memberQuery;

    /** 생성 (DL-01~04) — 회사의 모든 고객사에 가능하고, 배정 대상은 활성 구성원이다 */
    @Transactional
    public DealResponses.DealItem create(AccessContext ctx, DealRequests.CreateDeal request) {
        String customerName = customerQuery.get(ctx, request.customerId()).name();   // 없으면 404

        UUID assigneeId = request.assigneeMemberId() == null ? ctx.memberId() : request.assigneeMemberId();
        String assigneeName = requireActiveMemberName(ctx, assigneeId);

        Deal deal = dealRepository.save(Deal.create(ctx.companyId(), request.customerId(), assigneeId,
                request.title(), request.expectedAmount(), request.dueDate()));

        return DealResponses.DealItem.of(deal, customerName, assigneeName);
    }

    /**
     * 목록·보드 (DL-06·13·14).
     *
     * <p>영업(OWNED_ONLY)은 요청에 assigneeId를 무엇으로 넣든 <b>본인 담당만</b> 본다 (SC-02) —
     * 필터를 무시하는 게 아니라 범위가 그렇게 정의돼 있다. 기업 관리자는 회사 전체다 (SC-05).
     */
    public PageResponse<DealResponses.DealItem> list(AccessContext ctx, Deal.Stage stage,
                                                     UUID assigneeId, UUID customerId, Pageable pageable) {
        UUID scopedAssigneeId = ctx.scope() == AccessScope.OWNED_ONLY ? ctx.memberId() : assigneeId;

        Page<DealResponses.DealItem> page = dealRepository
                .search(ctx.companyId(), stage, scopedAssigneeId, customerId, pageable)
                .map(deal -> DealResponses.DealItem.of(deal,
                        customerQuery.get(ctx, deal.getCustomerId()).name(),
                        memberQuery.get(deal.getAssigneeMemberId()).name()));

        return PageResponse.from(page);
    }

    /** 상세 (DL-15·18) — 견적·주문 요약은 조회 창구가 정해질 때까지 빈 목록이다 */
    public DealResponses.DealDetail get(AccessContext ctx, UUID dealId) {
        Deal deal = findInScope(ctx, dealId);
        return DealResponses.DealDetail.of(deal,
                customerQuery.get(ctx, deal.getCustomerId()).name(),
                memberQuery.get(deal.getAssigneeMemberId()).name());
    }

    /** 제목·예상 금액·마감일 수정 (DL-02·03) — null 필드는 변경하지 않는다 */
    @Transactional
    public DealResponses.DealItem update(AccessContext ctx, UUID dealId, DealRequests.UpdateDeal request) {
        Deal deal = findInScope(ctx, dealId);
        deal.checkVersion(request.version());
        deal.update(request.title(), request.expectedAmount(), request.dueDate());

        return DealResponses.DealItem.of(deal,
                customerQuery.get(ctx, deal.getCustomerId()).name(),
                memberQuery.get(deal.getAssigneeMemberId()).name());
    }

    /**
     * 담당자 변경 (DL-05, SC-06) — <b>기업 관리자 전용</b>이다.
     *
     * <p>역할 판정을 컨트롤러가 아니라 여기서 한다 — 다른 호출 경로가 생겼을 때
     * 웹 계층에만 걸린 검사는 그대로 뚫린다. 역할로 갈리는 행위의 실패는 404가 아니라
     * <b>403 FORBIDDEN</b>이다 (Q-43, 권한 매트릭스의 ⭕/✕ 층).
     */
    @Transactional
    public DealResponses.DealItem changeAssignee(AccessContext ctx, UUID dealId,
                                                 DealRequests.ChangeAssignee request) {
        if (ctx.role() != Role.COMPANY_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Deal deal = findInScope(ctx, dealId);
        deal.checkVersion(request.version());

        String assigneeName = requireActiveMemberName(ctx, request.assigneeMemberId());
        deal.changeAssignee(request.assigneeMemberId());

        return DealResponses.DealItem.of(deal,
                customerQuery.get(ctx, deal.getCustomerId()).name(), assigneeName);
    }

    /**
     * 회사 스코프 + 미삭제 조회. 범위 밖이면 존재 여부를 구별하지 않고 404다 (SC-09).
     *
     * <p>영업이 남의 Deal을 지정한 경우도 여기서 404가 아니라, 아래 담당 판정에서 걸린다 —
     * 회사 안에는 존재하기 때문이다. 응답 문구는 동일하다.
     */
    private Deal findInScope(AccessContext ctx, UUID dealId) {
        Deal deal = dealRepository.findByIdAndCompanyIdAndDeletedAtIsNull(dealId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (ctx.scope() == AccessScope.OWNED_ONLY && !deal.getAssigneeMemberId().equals(ctx.memberId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);   // SC-02 — 권한 아닌 존재로 답한다
        }
        return deal;
    }

    /**
     * 배정 대상 검증 — 같은 회사의 활성 구성원이어야 한다 (DL-04·05, SC-06).
     *
     * <p>{@code MemberQuery.isActive}는 활성 여부만 답하고 소속 회사를 알려주지 않는다.
     * 그래서 회사의 활성 구성원 목록에서 찾는 방식으로 <b>소속과 활성을 한 번에</b> 판정한다
     * (구성원 5~30명 전제, 01 §2.1). 실패는 404다 (검증 노트 #3).
     */
    private String requireActiveMemberName(AccessContext ctx, UUID memberId) {
        return memberQuery.findAllActive(ctx.companyId()).stream()
                .filter(member -> member.id().equals(memberId))
                .findFirst()
                .map(MemberQuery.MemberSummary::name)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
