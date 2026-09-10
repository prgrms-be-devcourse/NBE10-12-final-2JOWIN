package com.twojo.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.QuoteQuery;
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
 * 기간 만료 배치 (Q-37) — <b>실제 DB로 돌린다</b>.
 *
 * <p>단위 테스트가 못 잡는 것을 본다: 파생 쿼리 {@code findIdsExpiredBefore}의 <b>날짜 경계</b>와
 * 실제 상태 전이·링크 만료가 커밋되는지다. {@code Before}가 하루 어긋나면 <b>아직 유효한 견적을
 * 닫아버린다</b> — 고객이 열람 중인 링크가 죽는 사고라 목으로는 증명이 안 된다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 워커가 {@code REQUIRES_NEW}로 열어 커밋해야
 * 재조회가 의미를 갖는다. 뒷정리는 {@code @AfterEach}에서 직접 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QuoteExpiryIntegrationTest {

    private static final LocalDate 오늘 = LocalDate.of(2026, 9, 11);

    @Autowired
    private QuoteExpiryBatch batch;
    @Autowired
    private QuoteQuery quoteQuery;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID dealId;

    @BeforeEach
    void 회사와_딜을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')", companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')",
                memberId, companyId, "sales-" + memberId + "@twojo.test", "박지훈");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "도담산업");
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'QUOTE', 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");
    }

    private UUID 견적(String quoteNo, String status, LocalDate validUntil) {
        UUID quoteId = UUID.randomUUID();
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, ?, 'EXCLUDED', 300000, 30000, 330000, ?, 0)",
                quoteId, companyId, dealId, quoteNo, status, validUntil);
        return quoteId;
    }

    private String 상태(UUID quoteId) {
        return jdbc.queryForObject("select status from quote where id = ?", String.class, quoteId);
    }

    /**
     * <b>이 테스트가 이 PR의 핵심이다.</b> 유효기간이 오늘인 견적은 오늘 자정까지 유효하다 —
     * 링크도 {@code valid_until 23:59:59}까지 살아 있다(Q-17). 여기가 하루 밀리면
     * 고객이 열람 중인 견적이 닫힌다.
     */
    @Test
    @DisplayName("유효기간이 오늘인 견적은 닫히지 않는다 — 오늘 자정까지 유효하다 (Q-17)")
    void 오늘_만료는_남는다() {
        UUID 어제 = 견적("Q-8001", "SENT", 오늘.minusDays(1));
        UUID 오늘_만료 = 견적("Q-8002", "SENT", 오늘);
        UUID 내일 = 견적("Q-8003", "VIEWED", 오늘.plusDays(1));

        batch.run(오늘);

        assertThat(상태(어제)).isEqualTo("EXPIRED");
        assertThat(상태(오늘_만료)).isEqualTo("SENT");     // 아직 유효하다
        assertThat(상태(내일)).isEqualTo("VIEWED");
    }

    @Test
    @DisplayName("발송됨·열람됨만 닫힌다 — 종결·작성 중은 유효기간이 지나도 그대로다")
    void 대상_상태만_닫힌다() {
        LocalDate 지난날 = 오늘.minusDays(5);
        UUID sent = 견적("Q-8010", "SENT", 지난날);
        UUID viewed = 견적("Q-8011", "VIEWED", 지난날);
        UUID draft = 견적("Q-8012", "DRAFT", 지난날);
        UUID approved = 견적("Q-8013", "APPROVED", 지난날);
        UUID rejected = 견적("Q-8014", "REJECTED", 지난날);
        UUID withdrawn = 견적("Q-8015", "WITHDRAWN", 지난날);

        batch.run(오늘);

        assertThat(상태(sent)).isEqualTo("EXPIRED");
        assertThat(상태(viewed)).isEqualTo("EXPIRED");
        assertThat(상태(draft)).isEqualTo("DRAFT");
        assertThat(상태(approved)).isEqualTo("APPROVED");
        assertThat(상태(rejected)).isEqualTo("REJECTED");
        assertThat(상태(withdrawn)).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("두 번 돌려도 결과가 같다 — 배치 재실행이 안전하다")
    void 멱등() {
        UUID quoteId = 견적("Q-8020", "SENT", 오늘.minusDays(3));

        batch.run(오늘);
        batch.run(오늘);

        assertThat(상태(quoteId)).isEqualTo("EXPIRED");
    }

    /**
     * 만료는 알림이 아니라 상태 전이라 정지 회사도 닫혀야 한다 (Q-27 —
     * {@code ViewTokenCommand.expire} javadoc: "회사 정지 중에도 만료 전이는 계속 돈다").
     * 이 배치가 {@code CompanyQuery}를 거치지 않는 근거를 실제로 고정한다.
     */
    @Test
    @DisplayName("정지 회사의 견적도 닫힌다 — 만료는 알림이 아니라 상태 전이다 (Q-27)")
    void 정지_회사도_닫힌다() {
        jdbc.update("update company set status = 'SUSPENDED' where id = ?", companyId);
        UUID quoteId = 견적("Q-8030", "SENT", 오늘.minusDays(2));

        batch.run(오늘);

        assertThat(상태(quoteId)).isEqualTo("EXPIRED");
    }

    /**
     * {@code findExpiringBetween}(NT-06)의 <b>구간 경계</b>를 같은 DB로 고정한다.
     * 계약이 "하한 포함 · 상한 포함"이라, {@code Between}이 그대로여야 D의 배치가
     * 임박 기준일 당일 견적을 놓치지 않는다.
     *
     * <p><b>하한이 없으면 이미 만료된 견적에 "임박했습니다" 메일이 나간다</b> —
     * 시그니처를 {@code (date)}에서 {@code (from, to)}로 넓힌 이유가 이것이다.
     */
    @Test
    @DisplayName("만료 임박은 양 끝을 포함한다 — 이미 지난 건은 빠진다 (NT-06)")
    void 임박_구간_경계() {
        견적("Q-8040", "SENT", 오늘.minusDays(1));      // 이미 지났다 — 하한 밖
        UUID 하한 = 견적("Q-8041", "SENT", 오늘);
        UUID 중간 = 견적("Q-8042", "VIEWED", 오늘.plusDays(1));
        UUID 상한 = 견적("Q-8043", "SENT", 오늘.plusDays(3));
        견적("Q-8044", "SENT", 오늘.plusDays(4));       // 상한 밖
        견적("Q-8045", "APPROVED", 오늘.plusDays(1));   // 응답이 끝났다

        List<String> 임박 = quoteQuery.findExpiringBetween(오늘, 오늘.plusDays(3)).stream()
                .map(QuoteQuery.QuoteSummary::quoteNo)
                .toList();

        assertThat(임박).containsExactlyInAnyOrder("Q-8041", "Q-8042", "Q-8043");
        assertThat(임박).doesNotContain("Q-8040", "Q-8044", "Q-8045");
        assertThat(하한).isNotNull();
        assertThat(중간).isNotNull();
        assertThat(상한).isNotNull();
    }

    /**
     * 계약이 <b>전 회사</b>를 한 번에 돌려주므로(2026-09-10 C·D 합의), 다른 회사 줄이
     * 함께 나오고 각 줄이 자기 {@code companyId}를 실어야 배치가 그룹핑할 수 있다.
     */
    @Test
    @DisplayName("만료 임박은 전 회사를 돌려준다 — 줄마다 companyId가 실린다")
    void 임박은_전_회사다() {
        견적("Q-8050", "SENT", 오늘.plusDays(1));

        List<QuoteQuery.QuoteSummary> rows = quoteQuery.findExpiringBetween(오늘, 오늘.plusDays(3));

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> assertThat(row.companyId()).isNotNull());
        assertThat(rows).anySatisfy(row -> assertThat(row.companyId()).isEqualTo(companyId));
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from quote_view_token where quote_id in (select id from quote where company_id = ?)",
                companyId);
        jdbc.update("delete from quote where company_id = ?", companyId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }
}
