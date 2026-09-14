package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.twojo.boundary.DealQuery;
import java.time.LocalDate;
import java.util.List;
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
 * 딜 요약의 성사 금액 (DL-18) — <b>실제 DB로 돌린다</b>.
 *
 * <p>고객사 상세의 Deal 이력(CU-12)이 성사 딜 금액을 "—"로 그렸다 — {@code DealQuery}가
 * {@code wonAmount}를 항상 null로 채우고 있었기 때문이다. 목 테스트는 창구가 시키는 대로 답하므로
 * 주문 행이 견적을 지나 실제로 딜에 모이는지는 여기서만 드러난다.
 *
 * <p><b>이 클래스가 뜨는 것 자체도 검증이다.</b> {@code DealQueryImpl}이 {@code QuoteQuery}를
 * 받게 되면서 {@code QuoteQueryImpl → DealQuery} 방향과 순환이 생길 수 있다. 컨텍스트가 올라오면
 * {@code ObjectProvider}로 끊은 것이 실제로 동작한다는 뜻이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DealSummaryWonAmountIntegrationTest {

    @Autowired
    private DealQuery dealQuery;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID wonDealId;
    private UUID openDealId;

    @BeforeEach
    void 심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        wonDealId = UUID.randomUUID();
        openDealId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')", companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, '김서연', 'COMPANY_ADMIN', 'ACTIVE')",
                memberId, companyId, "admin-" + memberId + "@twojo.test");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "성원산업");
        딜(wonDealId, "성원 사무의자 교체", "WON", 8_000_000L);
        딜(openDealId, "성원 회의실 리뉴얼", "QUOTE", 4_600_000L);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from orders where company_id = ?", companyId);
        jdbc.update("delete from quote where company_id = ?", companyId);
        jdbc.update("delete from deal where company_id = ?", companyId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    private void 딜(UUID dealId, String title, String stage, long expected) {
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, "
                        + "expected_amount, version) values (?, ?, ?, ?, ?, ?, ?, 0)",
                dealId, companyId, customerId, memberId, title, stage, expected);
    }

    private UUID 견적(UUID dealId, String quoteNo, String status, long total) {
        UUID quoteId = UUID.randomUUID();
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, ?, 'EXCLUDED', ?, 0, ?, ?, 0)",
                quoteId, companyId, dealId, quoteNo, status, total, total, LocalDate.now().plusDays(30));
        return quoteId;
    }

    private void 주문(UUID quoteId, String orderNo, long total) {
        jdbc.update("insert into orders (id, company_id, quote_id, order_no, "
                        + "supply_amount, vat_amount, total_amount) values (?, ?, ?, ?, ?, 0, ?)",
                UUID.randomUUID(), companyId, quoteId, orderNo, total, total);
    }

    /**
     * <b>이 테스트가 이 수정의 핵심이다.</b> 성사 딜 하나에 승인 견적 두 건·주문 두 건 —
     * 합계는 두 주문을 더한 값이고, 예상 금액과는 다르다. 진행 중 딜은 주문이 없어 null이다.
     */
    @Test
    @DisplayName("고객사 Deal 이력의 성사 딜에는 주문 합계가 실린다 (CU-12 · DL-18)")
    void 고객사_이력에_성사_금액이_실린다() {
        주문(견적(wonDealId, "Q-9101", "APPROVED", 1_320_000L), "O-9101", 1_320_000L);
        주문(견적(wonDealId, "Q-9102", "APPROVED", 880_000L), "O-9102", 880_000L);
        견적(openDealId, "Q-9103", "SENT", 4_600_000L);

        assertThat(dealQuery.summariesByCustomer(customerId))
                .extracting(DealQuery.DealSummary::title, DealQuery.DealSummary::expectedAmount,
                        DealQuery.DealSummary::wonAmount)
                .containsExactlyInAnyOrder(
                        tuple("성원 사무의자 교체", 8_000_000L, 2_200_000L),
                        tuple("성원 회의실 리뉴얼", 4_600_000L, null));
    }

    /** 계약 javadoc이 id 묶음 조회에도 성사 금액이 따라붙는다고 적는다 — 같은 규칙인지 본다 */
    @Test
    @DisplayName("id 묶음 조회도 같은 성사 금액을 싣는다 (DL-18)")
    void id_묶음_조회도_같다() {
        주문(견적(wonDealId, "Q-9201", "APPROVED", 8_800_000L), "O-9201", 8_800_000L);

        assertThat(dealQuery.summariesByIds(companyId, List.of(wonDealId, openDealId)))
                .extracting(DealQuery.DealSummary::title, DealQuery.DealSummary::wonAmount)
                .containsExactlyInAnyOrder(
                        tuple("성원 사무의자 교체", 8_800_000L),
                        tuple("성원 회의실 리뉴얼", null));
    }
}
