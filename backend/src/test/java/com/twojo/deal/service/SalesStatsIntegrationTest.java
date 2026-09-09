package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.boundary.SalesStatsQuery;
import com.twojo.boundary.SalesStatsQuery.StageCount;
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
 * 파이프라인 집계가 <b>실제 DB에서 도는지</b>를 본다 (DB-01).
 *
 * <p>{@code SalesStatsQueryImplTest}는 리포지토리가 목이라 <b>JPQL을 한 줄도 실행하지 않는다.</b>
 * 특히 {@code (:assigneeMemberId is null or ...)}은 PostgreSQL에서 파라미터 타입을 추론하지 못해
 * 실행 시점에 터지기 쉬운 모양이고, 그건 부트스트랩 검증에도 걸리지 않는다 —
 * 대시보드 첫 호출에서야 500으로 드러난다. 그 구멍을 메우는 것이 이 테스트다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다</b> — 뒷정리를 직접 한다
 * ({@code DealStageConcurrencyTest}와 같은 이유).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SalesStatsIntegrationTest {

    @Autowired
    private SalesStatsQuery salesStatsQuery;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID 박지훈;
    private UUID 다른영업;
    private UUID customerId;

    @BeforeEach
    void 단계별_딜을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        박지훈 = UUID.randomUUID();
        다른영업 = UUID.randomUUID();
        customerId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')", companyId, applicationId, "한빛오피스", businessNo);
        구성원(박지훈, "박지훈");
        구성원(다른영업, "이서준");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, 박지훈, "도담산업");

        딜(박지훈, "LEAD", 1_000_000L, null);
        딜(박지훈, "LEAD", 2_000_000L, null);
        딜(박지훈, "QUOTE", null, null);              // 예상 금액 미정 (DL-02) — coalesce 대상
        딜(다른영업, "LEAD", 9_000_000L, null);        // 남의 담당
        딜(박지훈, "WON", 5_000_000L, null);           // 종결 — 파이프라인 밖
        딜(박지훈, "CONSULT", 3_000_000L, "now()");    // 소프트 삭제
    }

    private void 구성원(UUID id, String name) {
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')",
                id, companyId, "sales-" + id + "@twojo.test", name);
    }

    private void 딜(UUID assignee, String stage, Long expected, String deletedAt) {
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, "
                        + "expected_amount, deleted_at, version) values (?, ?, ?, ?, ?, ?, ?, "
                        + (deletedAt == null ? "null" : deletedAt) + ", 0)",
                UUID.randomUUID(), companyId, customerId, assignee, "딜-" + stage, stage, expected);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from deal where company_id = ?", companyId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id in (?, ?)", 박지훈, 다른영업);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    private static long countOf(List<StageCount> rows, String stage) {
        return rows.stream().filter(r -> r.stage().equals(stage)).findFirst().orElseThrow().count();
    }

    private static Long amountOf(List<StageCount> rows, String stage) {
        return rows.stream().filter(r -> r.stage().equals(stage)).findFirst().orElseThrow().expectedAmountSum();
    }

    /**
     * <b>이 테스트가 실패하면 관리자 대시보드가 500이다.</b> 담당자 제한이 없는 경로는
     * JPQL에 {@code null} UUID가 바인딩되는 자리라, 목으로는 절대 드러나지 않는다.
     */
    @Test
    @DisplayName("기업 관리자는 회사 전체를 본다 — 담당자 파라미터가 null이어도 쿼리가 돈다 (SC-05)")
    void 관리자_집계() {
        List<StageCount> rows = salesStatsQuery.pipeline(
                new AccessContext(companyId, 박지훈, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL));

        assertThat(rows).extracting(StageCount::stage)
                .containsExactly("LEAD", "CONSULT", "QUOTE", "NEGOTIATION");
        assertThat(countOf(rows, "LEAD")).isEqualTo(3);           // 박지훈 2 + 타인 1
        assertThat(amountOf(rows, "LEAD")).isEqualTo(12_000_000L);
        assertThat(countOf(rows, "CONSULT")).isZero();            // 소프트 삭제 제외
        assertThat(countOf(rows, "NEGOTIATION")).isZero();        // 없는 단계도 0으로 선다
    }

    @Test
    @DisplayName("영업은 담당 딜만 집계된다 — 남의 딜이 섞이면 SC-02가 뚫린다")
    void 영업_집계() {
        List<StageCount> rows = salesStatsQuery.pipeline(
                new AccessContext(companyId, 박지훈, Role.SALES_REP, AccessScope.OWNED_ONLY));

        assertThat(countOf(rows, "LEAD")).isEqualTo(2);           // 타인의 1건 제외
        assertThat(amountOf(rows, "LEAD")).isEqualTo(3_000_000L);
    }

    /**
     * 종결(WON·LOST)은 파이프라인이 아니고, 예상 금액이 없는 딜(DL-02 미정)은 합에서 null이 아니라 0이어야 한다 —
     * null이 나가면 소비자가 다시 방어해야 한다.
     */
    @Test
    @DisplayName("종결은 빠지고, 예상 금액 미정 딜은 합이 null이 아니라 0이다")
    void 종결_제외와_금액_보정() {
        List<StageCount> rows = salesStatsQuery.pipeline(
                new AccessContext(companyId, 박지훈, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL));

        assertThat(rows).extracting(StageCount::stage).doesNotContain("WON", "LOST");
        assertThat(countOf(rows, "QUOTE")).isEqualTo(1);
        assertThat(amountOf(rows, "QUOTE")).isZero();             // expected_amount가 null인 딜 하나뿐
    }
}
