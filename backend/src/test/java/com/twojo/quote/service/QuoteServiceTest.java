package com.twojo.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.ProductQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.sequence.DocumentNumberService;
import com.twojo.global.sequence.DocumentSequence.DocType;
import com.twojo.quote.dto.QuoteRequests;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.repository.QuoteRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 견적 서비스 — 범위 판정(SC-01·02·09) · 상태 검증 · 카탈로그 스냅샷(QT-24)을 고정한다.
 *
 * <p>영업과 기업 관리자를 같은 요청으로 돌려 <b>스코프에 따라 답이 갈리는지</b>를 본다.
 * 견적은 담당자 컬럼이 없어 범위가 Deal에서 파생하므로(SC-02), 이 도메인에서 가장 조용히
 * 깨질 수 있는 규칙이다.
 */
@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID SALES_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, SALES_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);
    private static final AccessContext ADMIN =
            new AccessContext(COMPANY_ID, ADMIN_ID, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);

    @Mock private QuoteRepository quoteRepository;
    @Mock private DealQuery dealQuery;
    @Mock private ProductQuery productQuery;
    @Mock private DocumentNumberService documentNumberService;
    @InjectMocks private QuoteService quoteService;

    private static DealQuery.DealSummary dealSummary() {
        return new DealQuery.DealSummary(DEAL_ID, "한빛 사무가구 30석", "QUOTE", 5_000_000L, null, Instant.now());
    }

    /**
     * <b>저장된 뒤의</b> DRAFT 견적 — {@code version}을 0으로 채운다.
     *
     * <p>{@code Quote.draft()}가 막 만든 객체는 version이 null이다. JPA가 insert 시점에 0을
     * 넣기 때문인데, 그 상태로 두면 {@code checkVersion(0)}이 항상 STALE_VERSION을 던진다 —
     * 서비스가 다루는 것은 언제나 저장된 견적이므로, 여기서 현실에 없는 객체를 만들면
     * 거기서 나는 실패는 코드가 아니라 테스트의 문제다.
     */
    private static Quote draft() {
        Quote quote = Quote.draft(COMPANY_ID, DEAL_ID, "Q-2609-001", LocalDate.now().plusDays(30));
        ReflectionTestUtils.setField(quote, "version", 0);
        return quote;
    }

    /** 그 Deal이 이 요청의 범위 안에 있다고 본다 — 회사 일치 + (영업이면) 본인 담당 */
    private void dealIsVisibleTo(UUID memberId) {
        given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID))).willReturn(List.of(dealSummary()));
        given(dealQuery.assigneeIdOf(DEAL_ID)).willReturn(memberId);
    }

    private static QuoteRequests.UpdateQuote updateWith(List<QuoteRequests.UpdateQuote.Item> items) {
        return new QuoteRequests.UpdateQuote(LocalDate.now().plusDays(15), "EXCLUDED", "납기 2주", items, 0);
    }

    private static QuoteRequests.UpdateQuote.Item line(UUID productId, String name, Long unitPrice) {
        return new QuoteRequests.UpdateQuote.Item(productId, name, "개", 2, unitPrice, 0);
    }

    private static ErrorCode errorOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Nested
    @DisplayName("작성 시작 (QT-01)")
    class Create {

        @Test
        @DisplayName("진행 중 Deal이면 DRAFT와 채번된 번호가 붙는다")
        void 작성_시작() {
            dealIsVisibleTo(SALES_ID);
            given(dealQuery.isOpen(DEAL_ID)).willReturn(true);
            given(documentNumberService.next(COMPANY_ID, DocType.QUOTE)).willReturn("Q-2609-001");
            given(quoteRepository.save(any())).willAnswer(i -> i.getArgument(0));

            var created = quoteService.create(SALES, new QuoteRequests.CreateQuote(DEAL_ID));

            assertThat(created.quoteNo()).isEqualTo("Q-2609-001");
            assertThat(created.status()).isEqualTo("DRAFT");
            assertThat(created.vatMode()).isEqualTo("EXCLUDED");        // Q-16 기본
            assertThat(created.totalAmount()).isZero();                 // 항목은 PUT이 채운다
            assertThat(created.validUntil()).isAfter(LocalDate.now());  // NOT NULL 자리표시자
        }

        @Test
        @DisplayName("종결된 Deal에는 작성할 수 없다 — 채번을 소비하지 않는다 (Q-25)")
        void 종결_Deal_차단() {
            dealIsVisibleTo(SALES_ID);
            given(dealQuery.isOpen(DEAL_ID)).willReturn(false);

            assertThatThrownBy(() -> quoteService.create(SALES, new QuoteRequests.CreateQuote(DEAL_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteServiceTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_DEAL_CLOSED);

            // 번호를 먼저 뽑고 나서 막으면 실패한 요청마다 번호에 구멍이 생긴다
            then(documentNumberService).should(never()).next(any(), any());
            then(quoteRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("다른 회사의 Deal이면 404다 — 종결 여부를 묻지도 않는다 (SC-01·09)")
        void 남의_회사_Deal은_404() {
            given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID))).willReturn(List.of());

            assertThatThrownBy(() -> quoteService.create(SALES, new QuoteRequests.CreateQuote(DEAL_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteServiceTest::errorOf)
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

            then(dealQuery).should(never()).isOpen(any());
        }

        @Test
        @DisplayName("영업이 남의 담당 Deal에 작성하면 404다 — 403이 아니다 (SC-02·09)")
        void 남의_담당_Deal은_404() {
            dealIsVisibleTo(UUID.randomUUID());   // 다른 사람이 담당

            assertThatThrownBy(() -> quoteService.create(SALES, new QuoteRequests.CreateQuote(DEAL_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteServiceTest::errorOf)
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("기업 관리자는 담당이 아니어도 작성할 수 있다 (SC-05)")
        void 관리자는_담당_무관() {
            given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID))).willReturn(List.of(dealSummary()));
            given(dealQuery.isOpen(DEAL_ID)).willReturn(true);
            given(documentNumberService.next(COMPANY_ID, DocType.QUOTE)).willReturn("Q-2609-002");
            given(quoteRepository.save(any())).willAnswer(i -> i.getArgument(0));

            assertThat(quoteService.create(ADMIN, new QuoteRequests.CreateQuote(DEAL_ID)).quoteNo())
                    .isEqualTo("Q-2609-002");

            then(dealQuery).should(never()).assigneeIdOf(any());   // 담당 축을 보지 않는다
        }
    }

    @Nested
    @DisplayName("목록 (QT-20)")
    class Listing {

        @Test
        @DisplayName("영업은 담당 Deal id로 좁혀 조회한다 (SC-02)")
        void 영업은_담당_딜로_좁힌다() {
            List<UUID> assigned = List.of(DEAL_ID);
            given(dealQuery.assignedDealIds(COMPANY_ID, SALES_ID)).willReturn(assigned);
            given(quoteRepository.search(any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of()));

            quoteService.list(SALES, null, null, PageRequest.of(0, 20));

            ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.captor();
            then(quoteRepository).should()
                    .search(eq(COMPANY_ID), eq(null), eq(null), captor.capture(), any());
            assertThat(captor.getValue()).isEqualTo(assigned);
        }

        @Test
        @DisplayName("기업 관리자는 제한 없이 조회한다 — 담당 딜을 묻지 않는다 (SC-05)")
        void 관리자는_제한_없음() {
            given(quoteRepository.search(any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of()));

            quoteService.list(ADMIN, null, null, PageRequest.of(0, 20));

            ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.captor();
            then(quoteRepository).should()
                    .search(eq(COMPANY_ID), eq(null), eq(null), captor.capture(), any());
            assertThat(captor.getValue()).isNull();   // null = 제한 없음
            then(dealQuery).should(never()).assignedDealIds(any(), any());
        }
    }

    @Nested
    @DisplayName("수정 (QT-02~11·23)")
    class Update {

        private void quoteExists(Quote quote) {
            given(quoteRepository.findWithItemsByIdAndCompanyId(QUOTE_ID, COMPANY_ID))
                    .willReturn(Optional.of(quote));
            dealIsVisibleTo(SALES_ID);
        }

        @Test
        @DisplayName("카탈로그 항목은 품목명·단위를 카탈로그에서 가져온다 — 요청 값을 믿지 않는다 (QT-24)")
        void 카탈로그_값_복사() {
            quoteExists(draft());
            given(productQuery.isSellable(SALES, PRODUCT_ID)).willReturn(true);
            given(productQuery.get(SALES, PRODUCT_ID)).willReturn(
                    new ProductQuery.ProductSnapshot(PRODUCT_ID, "1200 사무책상", "개", 180_000L));

            var result = quoteService.update(SALES, QUOTE_ID,
                    updateWith(List.of(line(PRODUCT_ID, "클라이언트가 보낸 엉뚱한 이름", 170_000L))));

            var item = result.items().getFirst();
            assertThat(item.name()).isEqualTo("1200 사무책상");            // 요청 값이 아니다
            assertThat(item.unitPrice()).isEqualTo(170_000L);              // 단가만 요청 값 (QT-05)
            assertThat(item.catalogPriceAtCreation()).isEqualTo(180_000L); // 그때 카탈로그가 얼마였나
            assertThat(result.supplyAmount()).isEqualTo(340_000L);         // 170,000 x 2
            assertThat(result.vatAmount()).isEqualTo(34_000L);
        }

        @Test
        @DisplayName("직접 입력 항목은 요청 값을 그대로 쓰고 카탈로그 단가를 남기지 않는다 (QT-03)")
        void 직접_입력() {
            quoteExists(draft());

            var result = quoteService.update(SALES, QUOTE_ID,
                    updateWith(List.of(line(null, "현장 실측", 300_000L))));

            var item = result.items().getFirst();
            assertThat(item.name()).isEqualTo("현장 실측");
            assertThat(item.productId()).isNull();
            assertThat(item.catalogPriceAtCreation()).isNull();
            then(productQuery).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("판매 중지 상품은 담을 수 없다 (PR-06)")
        void 판매_중지_차단() {
            quoteExists(draft());
            given(productQuery.isSellable(SALES, PRODUCT_ID)).willReturn(false);

            assertThatThrownBy(() -> quoteService.update(SALES, QUOTE_ID,
                    updateWith(List.of(line(PRODUCT_ID, "구형 책상", 100_000L)))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteServiceTest::errorOf)
                    .isEqualTo(ErrorCode.PRODUCT_DISCONTINUED);

            then(productQuery).should(never()).get(any(), any());
        }

        @Test
        @DisplayName("낡은 version이면 항목을 조립하기 전에 막힌다 — 카탈로그를 조회하지 않는다")
        void 낡은_version() {
            Quote quote = draft();
            ReflectionTestUtils.setField(quote, "version", 3);
            quoteExists(quote);

            assertThatThrownBy(() -> quoteService.update(SALES, QUOTE_ID,
                    updateWith(List.of(line(PRODUCT_ID, "책상", 100_000L)))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteServiceTest::errorOf)
                    .isEqualTo(ErrorCode.STALE_VERSION);

            then(productQuery).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("DRAFT가 아니면 수정할 수 없다 (QT-16)")
        void 발송된_견적은_불변() {
            Quote quote = draft();
            ReflectionTestUtils.setField(quote, "status", Quote.Status.SENT);
            quoteExists(quote);

            assertThatThrownBy(() -> quoteService.update(SALES, QUOTE_ID,
                    updateWith(List.of(line(null, "항목", 100_000L)))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteServiceTest::errorOf)
                    .isEqualTo(ErrorCode.QUOTE_NOT_DRAFT);
        }

        @Test
        @DisplayName("모르는 vat_mode는 500이 아니라 400이다")
        void 잘못된_vat_mode() {
            quoteExists(draft());

            var request = new QuoteRequests.UpdateQuote(LocalDate.now().plusDays(15), "INCLUSIVE",
                    null, List.of(line(null, "항목", 100_000L)), 0);

            assertThatThrownBy(() -> quoteService.update(SALES, QUOTE_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteServiceTest::errorOf)
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }
    }

    @Nested
    @DisplayName("단건 조회·미리보기")
    class Read {

        @Test
        @DisplayName("없는 견적은 404다 (SC-09)")
        void 없는_견적() {
            given(quoteRepository.findWithItemsByIdAndCompanyId(QUOTE_ID, COMPANY_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> quoteService.get(SALES, QUOTE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(QuoteServiceTest::errorOf)
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("미리보기는 고객이 볼 것과 같은 데이터를 돌려준다 (QT-12)")
        void 미리보기() {
            given(quoteRepository.findWithItemsByIdAndCompanyId(QUOTE_ID, COMPANY_ID))
                    .willReturn(Optional.of(draft()));
            dealIsVisibleTo(SALES_ID);

            var view = quoteService.preview(SALES, QUOTE_ID);

            assertThat(view.quoteNo()).isEqualTo("Q-2609-001");
            assertThat(view.companyId()).isEqualTo(COMPANY_ID);   // D가 회사 축으로 쓴다
            assertThat(view.dealId()).isEqualTo(DEAL_ID);         // D가 현재 담당자를 찾는 축 (AP-18)
        }
    }
}
