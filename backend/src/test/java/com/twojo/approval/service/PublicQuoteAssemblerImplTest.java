package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.PublicQuoteResponse;
import com.twojo.boundary.QuoteQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PublicQuoteAssemblerImpl} — 네 boundary 조회를 {@link PublicQuoteResponse} 하나로 말아주는지,
 * respondable 규칙과 항목 정렬이 계약대로인지, 내부 식별자가 새지 않는지 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PublicQuoteAssemblerImplTest {

    private static final UUID QUOTE_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID DEAL_ID = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID ASSIGNEE_ID = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final String QUOTE_NO = "Q-2609-014";

    @Mock
    private QuoteQuery quoteQuery;
    @Mock
    private CompanyQuery companyQuery;
    @Mock
    private DealQuery dealQuery;
    @Mock
    private MemberQuery memberQuery;
    @InjectMocks
    private PublicQuoteAssemblerImpl assembler;

    private static QuoteQuery.PublicQuoteView view(String status, List<QuoteQuery.PublicQuoteView.Item> items) {
        return new QuoteQuery.PublicQuoteView(QUOTE_ID, QUOTE_NO, status, "EXCLUSIVE",
                "설치는 납품일로부터 3일 이내", LocalDate.of(2026, 9, 20),
                1_000_000L, 100_000L, 1_100_000L, items, DEAL_ID, COMPANY_ID);
    }

    private void givenChain(QuoteQuery.PublicQuoteView view, boolean companyActive) {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view);
        given(companyQuery.get(COMPANY_ID))
                .willReturn(new CompanyQuery.CompanySummary(COMPANY_ID, "한빛산업", "123-45-67890", companyActive));
        given(dealQuery.assigneeIdOf(DEAL_ID)).willReturn(ASSIGNEE_ID);
        given(memberQuery.getContact(ASSIGNEE_ID))
                .willReturn(new MemberQuery.MemberContact("박지훈", "jihun@hanbit.co.kr", "010-1000-2000"));
    }

    @Test
    @DisplayName("assembleForView가 회사명·사업자번호·담당자·respondable을 채운다")
    void view가_네_필드를_채운다() {
        givenChain(view("VIEWED", List.of()), true);

        PublicQuoteResponse res = assembler.assembleForView(QUOTE_ID, true);

        assertThat(res.quoteNo()).isEqualTo(QUOTE_NO);
        assertThat(res.status()).isEqualTo("VIEWED");
        assertThat(res.companyName()).isEqualTo("한빛산업");
        assertThat(res.companyBusinessNo()).isEqualTo("123-45-67890");
        assertThat(res.assignee())
                .isEqualTo(new PublicQuoteResponse.AssigneeInfo("박지훈", "jihun@hanbit.co.kr", "010-1000-2000"));
        assertThat(res.respondable()).isTrue();
    }

    @Test
    @DisplayName("PublicQuoteResponse에는 dealId·companyId 컴포넌트가 없다")
    void 응답에_내부_식별자가_없다() {
        assertThat(PublicQuoteResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("dealId", "companyId");
    }

    @Test
    @DisplayName("assembleForPreview는 respondable=false 고정, DRAFT도 예외 없이 조립한다")
    void preview는_DRAFT를_조립하고_respondable은_false다() {
        givenChain(view("DRAFT", List.of()), true);

        PublicQuoteResponse res = assembler.assembleForPreview(QUOTE_ID);

        assertThat(res.status()).isEqualTo("DRAFT");
        assertThat(res.companyName()).isEqualTo("한빛산업");
        assertThat(res.respondable()).isFalse();
    }

    @Test
    @DisplayName("assembleForPreview는 SENT·활성 견적을 받아도 respondable=false다")
    void preview는_응답_가능_상태여도_false다() {
        givenChain(view("SENT", List.of()), true);

        assertThat(assembler.assembleForPreview(QUOTE_ID).respondable()).isFalse();
    }

    @ParameterizedTest(name = "status={0}, active={1}, link={2} -> respondable={3}")
    @CsvSource({
            "SENT,      true,  true,  true",
            "VIEWED,    true,  true,  true",
            "RESPONDED, true,  true,  false",
            "DRAFT,     true,  true,  false",
            "VIEWED,    false, true,  false",   // 정지 회사
            "VIEWED,    true,  false, false",   // 만료·소진 링크
    })
    @DisplayName("assembleForView respondable = 링크 && 회사 active && status in {SENT,VIEWED}")
    void view_respondable_규칙(String status, boolean active, boolean link, boolean expected) {
        givenChain(view(status, List.of()), active);

        assertThat(assembler.assembleForView(QUOTE_ID, link).respondable()).isEqualTo(expected);
    }

    @Test
    @DisplayName("담당자가 바뀌면 조립 결과의 assignee도 따라 바뀐다 (AP-18)")
    void 담당자_교체가_반영된다() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view("VIEWED", List.of()));
        given(companyQuery.get(COMPANY_ID))
                .willReturn(new CompanyQuery.CompanySummary(COMPANY_ID, "한빛산업", "123-45-67890", true));
        UUID newAssignee = UUID.fromString("d0000000-0000-4000-8000-000000000099");
        given(dealQuery.assigneeIdOf(DEAL_ID)).willReturn(newAssignee);
        given(memberQuery.getContact(newAssignee))
                .willReturn(new MemberQuery.MemberContact("최선진", "sunny@hanbit.co.kr", "010-3000-4000"));

        PublicQuoteResponse res = assembler.assembleForView(QUOTE_ID, true);

        assertThat(res.assignee().name()).isEqualTo("최선진");
        assertThat(res.assignee().email()).isEqualTo("sunny@hanbit.co.kr");
    }

    @Test
    @DisplayName("항목은 sortOrder 오름차순으로 정렬된다")
    void 항목은_sortOrder로_정렬된다() {
        List<QuoteQuery.PublicQuoteView.Item> unordered = List.of(
                new QuoteQuery.PublicQuoteView.Item("설치비", "식", 1, 200_000L, 200_000L, 2),
                new QuoteQuery.PublicQuoteView.Item("본체", "대", 1, 800_000L, 800_000L, 0),
                new QuoteQuery.PublicQuoteView.Item("케이블", "m", 10, 1_000L, 10_000L, 1));
        givenChain(view("VIEWED", unordered), true);

        PublicQuoteResponse res = assembler.assembleForView(QUOTE_ID, true);

        assertThat(res.items()).extracting(PublicQuoteResponse.ItemView::name)
                .containsExactly("본체", "케이블", "설치비");
    }

    @Test
    @DisplayName("getPublicView가 RESOURCE_NOT_FOUND를 던지면 그대로 전파한다")
    void 견적이_없으면_전파한다() {
        given(quoteQuery.getPublicView(QUOTE_ID))
                .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> assembler.assembleForView(QUOTE_ID, true))
                .isInstanceOf(BusinessException.class);
    }
}
