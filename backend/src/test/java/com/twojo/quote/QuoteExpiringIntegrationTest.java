package com.twojo.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.QuoteQuery;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * {@code QuoteQuery.findExpiringBetween}의 <b>실 PG로만 잡히는 것</b> (NT-06, #313).
 *
 * <ul>
 *   <li><b>구간 양 끝 포함 · 이미 지난 건 제외</b> — {@code Between} 파생 쿼리가 실제로 어떤 SQL로
 *       번역되는지는 목으로 알 수 없다. 하한이 빠지면 이미 만료된 견적에 "임박했습니다"가 나간다.</li>
 *   <li><b>전 회사 반환에 {@code companyId}가 실리고 이름은 회사별로 맞는다</b> — 실 {@code DealQuery}·
 *       {@code CustomerQuery}가 회사 스코프를 거는 상태에서 다른 회사 이름이 새지 않는지 본다.</li>
 * </ul>
 *
 * <p>계약이 <b>전 회사</b>를 돌려주므로 결과에 다른 테스트의 잔여 행이 섞일 수 있다 — 단언은 여기서
 * 심은 견적으로 거른다. 배치 end-to-end(Q-27 정지 억제 포함)는 D의 {@code ExpiringQuoteIntegrationTest} 몫이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QuoteExpiringIntegrationTest {

    private static final LocalDate FROM = LocalDate.now();
    private static final LocalDate TO = FROM.plusDays(3);

    @Autowired
    private QuoteQuery quoteQuery;
    @Autowired
    private JdbcTemplate jdbc;

    private Company mine;
    private Company theirs;
    private final List<UUID> quoteIds = new ArrayList<>();

    /** 회사 하나의 픽스처 — 신청·회사·담당자·고객사·딜까지. 견적은 테스트가 심는다 */
    private record Company(UUID applicationId, UUID id, UUID memberId, UUID customerId, UUID dealId) {}

    @BeforeEach
    void 회사_둘을_심는다() {
        mine = company("한빛오피스", "도담산업", "도담 사무가구");
        theirs = company("성원물산", "성원건설", "성원 확장");
    }

    @AfterEach
    void 지운다() {
        for (UUID quoteId : quoteIds) {
            jdbc.update("delete from quote where id = ?", quoteId);
        }
        quoteIds.clear();
        for (Company c : List.of(mine, theirs)) {
            jdbc.update("delete from deal where id = ?", c.dealId());
            jdbc.update("delete from customer where id = ?", c.customerId());
            jdbc.update("delete from member where id = ?", c.memberId());
            jdbc.update("delete from company where id = ?", c.id());
            jdbc.update("delete from application where id = ?", c.applicationId());
        }
    }

    @Test
    @DisplayName("구간 양 끝은 포함하고 하한 전(이미 만료)·상한 후·작성 중은 제외한다")
    void 양_끝_포함_이미_지난_건_제외() {
        UUID expired = quote(mine, "SENT", FROM.minusDays(1));     // 이미 만료 — 하한이 거른다
        UUID atFrom = quote(mine, "SENT", FROM);                   // 하한 포함
        UUID atTo = quote(mine, "VIEWED", TO);                     // 상한 포함
        UUID afterTo = quote(mine, "SENT", TO.plusDays(1));        // 아직 멀다
        UUID draft = quote(mine, "DRAFT", FROM);                   // 발송 전 — 상태가 거른다

        List<UUID> found = quoteQuery.findExpiringBetween(FROM, TO).stream()
                .map(QuoteQuery.QuoteSummary::id)
                .filter(quoteIds::contains)
                .toList();

        assertThat(found).containsExactly(atFrom, atTo);           // 회사 안에서는 임박 순
        assertThat(found).doesNotContain(expired, afterTo, draft);
    }

    @Test
    @DisplayName("전 회사가 한 번에 나오고 줄마다 companyId·자기 회사의 고객사명이 실린다")
    void 전_회사_반환_companyId() {
        UUID ofMine = quote(mine, "SENT", FROM.plusDays(1));
        UUID ofTheirs = quote(theirs, "VIEWED", FROM.plusDays(2));

        List<QuoteQuery.QuoteSummary> rows = quoteQuery.findExpiringBetween(FROM, TO).stream()
                .filter(row -> quoteIds.contains(row.id()))
                .toList();

        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(row -> row.id().equals(ofMine)).singleElement()
                .satisfies(row -> {
                    assertThat(row.companyId()).isEqualTo(mine.id());
                    assertThat(row.customerName()).isEqualTo("도담산업");
                });
        assertThat(rows).filteredOn(row -> row.id().equals(ofTheirs)).singleElement()
                .satisfies(row -> {
                    assertThat(row.companyId()).isEqualTo(theirs.id());
                    assertThat(row.customerName()).isEqualTo("성원건설");   // 남의 고객사명이 아니다
                });
    }

    private Company company(String companyName, String customerName, String dealTitle) {
        Company c = new Company(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        String businessNo = c.applicationId().toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                c.applicationId(), companyName, businessNo, "admin-" + c.applicationId() + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')",
                c.id(), c.applicationId(), companyName, businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')",
                c.memberId(), c.id(), "sales-" + c.memberId() + "@twojo.test", "박지훈");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                c.customerId(), c.id(), c.memberId(), customerName);
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'QUOTE', 0)",
                c.dealId(), c.id(), c.customerId(), c.memberId(), dealTitle);
        return c;
    }

    private UUID quote(Company c, String status, LocalDate validUntil) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, ?, 'EXCLUDED', 300000, 30000, 330000, ?, 0)",
                id, c.id(), c.dealId(), "Q-EXP-" + id.toString().substring(0, 8), status, validUntil);
        quoteIds.add(id);
        return id;
    }
}
