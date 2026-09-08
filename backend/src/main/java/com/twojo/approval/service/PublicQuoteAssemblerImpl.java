package com.twojo.approval.service;

import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.PublicQuoteAssembler;
import com.twojo.boundary.PublicQuoteResponse;
import com.twojo.boundary.QuoteQuery;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link PublicQuoteAssembler} 구현 — 고객 열람(approval)과 구성원 미리보기(quote)가 공유하는
 * {@link PublicQuoteResponse} 조립기.
 *
 * <p><b>무트랜잭션이다</b> — {@code getPublicView}·{@link CompanyQuery}·{@link DealQuery}·{@link MemberQuery}
 * 네 조회는 각 구현이 자체 readOnly 트랜잭션을 잡았다 놓는다. 여기서 트랜잭션을 열면 네 호출이
 * 커넥션 하나를 조립이 끝날 때까지 점유한다 — {@code CustomerQuoteService.view}가 피하는 것과 같은 이유다.
 *
 * <p>스코프 판정·토큰 검증·DRAFT 차단·첫 열람 부수효과는 호출자 몫이다
 * (계약 {@link PublicQuoteAssembler} javadoc 참조).
 */
@Service
@RequiredArgsConstructor
class PublicQuoteAssemblerImpl implements PublicQuoteAssembler {

    private final QuoteQuery quoteQuery;
    private final CompanyQuery companyQuery;
    private final DealQuery dealQuery;
    private final MemberQuery memberQuery;

    @Override
    public PublicQuoteResponse assembleForPreview(UUID quoteId) {
        return assemble(quoteId, false);   // 발송 전 — 링크가 없어 respondable 판정 대상이 아니다
    }

    @Override
    public PublicQuoteResponse assembleForView(UUID quoteId, boolean linkRespondable) {
        return assemble(quoteId, linkRespondable);
    }

    private PublicQuoteResponse assemble(UUID quoteId, boolean linkRespondable) {
        QuoteQuery.PublicQuoteView view = quoteQuery.getPublicView(quoteId);   // 없으면 RESOURCE_NOT_FOUND 전파
        CompanyQuery.CompanySummary company = companyQuery.get(view.companyId());
        MemberQuery.MemberContact assignee =
                memberQuery.getContact(dealQuery.assigneeIdOf(view.dealId()));   // AP-18: Deal의 현재 담당자

        boolean respondable = linkRespondable
                && company.active()
                && ("SENT".equals(view.status()) || "VIEWED".equals(view.status()));

        List<PublicQuoteResponse.ItemView> items = view.items().stream()
                .sorted(Comparator.comparingInt(QuoteQuery.PublicQuoteView.Item::sortOrder))
                .map(i -> new PublicQuoteResponse.ItemView(
                        i.name(), i.unit(), i.quantity(), i.unitPrice(), i.amount()))
                .toList();

        return new PublicQuoteResponse(
                view.quoteNo(), view.status(), company.name(), company.businessNo(),
                new PublicQuoteResponse.AssigneeInfo(assignee.name(), assignee.email(), assignee.phone()),
                view.vatMode(), view.terms(), view.validUntil(),
                view.supplyAmount(), view.vatAmount(), view.totalAmount(),
                items, respondable);
        // dealId·companyId는 옮겨 담지 않는다 — 조회에만 쓰고 고객 화면 응답에는 싣지 않는다
    }
}
