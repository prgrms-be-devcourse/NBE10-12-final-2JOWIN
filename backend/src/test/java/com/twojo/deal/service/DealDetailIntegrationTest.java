package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.OrderQuery;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.Role;
import com.twojo.deal.dto.DealResponses;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 딜 상세의 견적·주문 탭 (DL-15·18, #304) — <b>실제 DB로 돌린다</b>.
 *
 * <p>단위 테스트가 못 잡는 것을 본다: 두 경계 창구의 파생 쿼리가 <b>회사 스코프를 실제로
 * 거는지</b>다. {@code briefsByDeals}·{@code briefsByQuotes}가 회사 조건을 빠뜨리면
 * 목에서는 시키는 대로 답하지만 실제로는 <b>남의 회사 견적·주문이 딜 상세에 붙는다</b> —
 * SC-01이 통째로 뚫리는 자리라 목으로는 증명이 안 된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DealDetailIntegrationTest {

    @Autowired
    private DealService dealService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private QuoteQuery quoteQuery;
    @Autowired
    private OrderQuery orderQuery;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID dealId;
    private AccessContext ctx;

    /** 남의 회사 — 같은 deal_id·quote_id로 물었을 때 섞여 나오면 안 된다 */
    private UUID otherApplicationId;
    private UUID otherCompanyId;

    @BeforeEach
    void 심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        otherApplicationId = UUID.randomUUID();
        otherCompanyId = UUID.randomUUID();

        회사(applicationId, companyId, "한빛오피스");
        회사(otherApplicationId, otherCompanyId, "성원물산");

        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, ?, 'COMPANY_ADMIN', 'ACTIVE')",
                memberId, companyId, "admin-" + memberId + "@twojo.test", "김서연");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "도담산업");
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, "
                        + "expected_amount, version) values (?, ?, ?, ?, ?, 'WON', 5000000, 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");

        ctx = new AccessContext(companyId, memberId, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    }

    private void 회사(UUID appId, UUID coId, String name) {
        String businessNo = appId.toString().substring(0, 13);
        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                appId, name, businessNo, "admin-" + appId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')", coId, appId, name, businessNo);
    }

    private UUID 견적(UUID ownerCompanyId, UUID ownerDealId, String quoteNo, String status, long total) {
        UUID quoteId = UUID.randomUUID();
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, ?, 'EXCLUDED', ?, 0, ?, ?, 0)",
                quoteId, ownerCompanyId, ownerDealId, quoteNo, status, total, total,
                LocalDate.now().plusDays(30));
        return quoteId;
    }

    private void 주문(UUID ownerCompanyId, UUID quoteId, String orderNo, long total) {
        jdbc.update("insert into orders (id, company_id, quote_id, order_no, "
                        + "supply_amount, vat_amount, total_amount) values (?, ?, ?, ?, ?, 0, ?)",
                UUID.randomUUID(), ownerCompanyId, quoteId, orderNo, total, total);
    }

    @Test
    @DisplayName("견적과 주문이 딜 상세에 실린다 — 주문은 견적을 한 홉 지나 붙는다")
    void 두_홉이_실제로_이어진다() {
        UUID quoteId = 견적(companyId, dealId, "Q-7001", "APPROVED", 1_320_000L);
        주문(companyId, quoteId, "O-7001", 1_320_000L);

        DealResponses.DealDetail detail = dealService.get(ctx, dealId);

        assertThat(detail.quotes()).extracting(DealResponses.DealDetail.QuoteSummary::quoteNo)
                .containsExactly("Q-7001");
        assertThat(detail.orders()).extracting(DealResponses.DealDetail.OrderSummary::orderNo)
                .containsExactly("O-7001");
        assertThat(detail.wonAmount()).isEqualTo(1_320_000L);
    }

    /**
     * <b>이 테스트가 이 PR의 핵심이다.</b> 두 파생 쿼리에서 회사 조건이 빠져도 목 테스트는
     * 전부 통과한다 — 실제 DB에서만 드러난다.
     *
     * <p>딜 상세를 거치지 않고 <b>경계 창구를 직접 부른다</b>. 서비스 경로로는 이 구멍을
     * 만들 수 없기 때문이다 — {@code fk_quote_deal}이 남의 회사가 같은 {@code deal_id}를
     * 쓰는 것을 막아, 회사 조건이 없어도 {@code dealId} 필터에 걸려 통과해버린다.
     * 같은 id를 <b>다른 회사로 물었을 때 빈 목록인지</b>가 스코프의 실제 판정이다.
     */
    @Test
    @DisplayName("같은 id를 남의 회사로 물으면 빈 목록이다 — 회사 스코프가 실제로 걸린다 (SC-01)")
    void 회사_스코프() {
        UUID quoteId = 견적(companyId, dealId, "Q-7010", "APPROVED", 1_000_000L);
        주문(companyId, quoteId, "O-7010", 1_000_000L);

        assertThat(quoteQuery.briefsByDeals(companyId, java.util.List.of(dealId))).hasSize(1);
        assertThat(orderQuery.briefsByQuotes(companyId, java.util.List.of(quoteId))).hasSize(1);

        assertThat(quoteQuery.briefsByDeals(otherCompanyId, java.util.List.of(dealId))).isEmpty();
        assertThat(orderQuery.briefsByQuotes(otherCompanyId, java.util.List.of(quoteId))).isEmpty();
    }

    /**
     * 빈 묶음에 회사 전체 주문이 붙으면 그대로 사고다 — {@code wonTotalsByQuotes}가
     * null을 "제한 없음"으로 읽는 것과 갈리는 지점이라(계약 javadoc) 실제로 고정한다.
     */
    @Test
    @DisplayName("빈 묶음을 넘기면 빈 목록이다 — 회사 전체가 붙지 않는다")
    void 빈_묶음은_빈_목록() {
        UUID quoteId = 견적(companyId, dealId, "Q-7015", "APPROVED", 1_000_000L);
        주문(companyId, quoteId, "O-7015", 1_000_000L);

        assertThat(orderQuery.briefsByQuotes(companyId, java.util.List.of())).isEmpty();
        assertThat(quoteQuery.briefsByDeals(companyId, java.util.List.of())).isEmpty();
    }

    @Test
    @DisplayName("회수·반려된 견적도 이력으로 나온다 — 그 딜에서 무슨 견적이 오갔는지 보여주는 자리다")
    void 종결_견적도_이력이다() {
        견적(companyId, dealId, "Q-7020", "WITHDRAWN", 500_000L);
        견적(companyId, dealId, "Q-7021", "REJECTED", 600_000L);

        DealResponses.DealDetail detail = dealService.get(ctx, dealId);

        assertThat(detail.quotes()).extracting(DealResponses.DealDetail.QuoteSummary::quoteNo)
                .containsExactlyInAnyOrder("Q-7020", "Q-7021");
        assertThat(detail.orders()).isEmpty();
    }

    /**
     * <b>목이 이미 최신순으로 그린다</b>({@code mocks/handlers/deal.ts} — {@code createdAt} 내림차순).
     * 서버가 순서를 안 정하면 화면 순서가 환경마다 갈린다. 복제(QT-19)로 재제안한 건이
     * 원본 아래 묻히면 담당자가 옛 견적을 보게 되는 자리라 계약으로 고정한다.
     */
    @Test
    @DisplayName("견적·주문이 최근 것부터 나온다 — 목과 같은 순서다")
    void 최신순_정렬() {
        UUID first = 견적(companyId, dealId, "Q-7030", "REJECTED", 500_000L);
        주문(companyId, first, "O-7030", 500_000L);
        jdbc.update("update quote set created_at = now() - interval '2 days' where id = ?", first);
        jdbc.update("update orders set created_at = now() - interval '2 days' where quote_id = ?", first);

        UUID second = 견적(companyId, dealId, "Q-7031", "APPROVED", 900_000L);
        주문(companyId, second, "O-7031", 900_000L);

        DealResponses.DealDetail detail = dealService.get(ctx, dealId);

        assertThat(detail.quotes()).extracting(DealResponses.DealDetail.QuoteSummary::quoteNo)
                .containsExactly("Q-7031", "Q-7030");
        assertThat(detail.orders()).extracting(DealResponses.DealDetail.OrderSummary::orderNo)
                .containsExactly("O-7031", "O-7030");
    }

    @AfterEach
    void 지운다() {
        for (UUID co : java.util.List.of(companyId, otherCompanyId)) {
            jdbc.update("delete from orders where company_id = ?", co);
            jdbc.update("delete from quote where company_id = ?", co);
        }
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id in (?, ?)", companyId, otherCompanyId);
        jdbc.update("delete from application where id in (?, ?)", applicationId, otherApplicationId);
    }
}
