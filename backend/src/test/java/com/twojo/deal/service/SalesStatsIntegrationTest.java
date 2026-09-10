package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.boundary.SalesStatsQuery;
import com.twojo.boundary.SalesStatsQuery.MemberPerformance;
import com.twojo.boundary.SalesStatsQuery.StageCount;
import com.twojo.boundary.SalesStatsQuery.WonStats;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
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

    private UUID 딜(UUID assignee, String stage, Long expected, String deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, "
                        + "expected_amount, deleted_at, version) values (?, ?, ?, ?, ?, ?, ?, "
                        + (deletedAt == null ? "null" : deletedAt) + ", 0)",
                id, companyId, customerId, assignee, "딜-" + stage, stage, expected);
        return id;
    }

    /**
     * 승인 견적 + 그 견적이 만든 주문 하나를 심는다.
     *
     * <p>{@code convertedAt}을 직접 넣는 이유는 <b>기간 경계를 시험해야</b> 하기 때문이다 —
     * {@code orders.created_at}이 전환 시각이고(별도 컬럼이 없다), 기본값 {@code now()}로는
     * 지난달·이달을 갈라 심을 수 없다.
     */
    private void 전환된_주문(UUID dealId, long total, OffsetDateTime convertedAt) {
        UUID quoteId = UUID.randomUUID();
        String no = quoteId.toString().substring(0, 8);
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, 'APPROVED', 'EXCLUDED', ?, ?, ?, ?, 0)",
                quoteId, companyId, dealId, "Q-" + no,
                total * 10 / 11, total - total * 10 / 11, total, LocalDate.now().plusDays(30));
        jdbc.update("insert into orders (id, company_id, quote_id, order_no, supply_amount, "
                        + "vat_amount, total_amount, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), companyId, quoteId, "O-" + no,
                total * 10 / 11, total - total * 10 / 11, total, convertedAt);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from orders where company_id = ?", companyId);
        jdbc.update("delete from quote where company_id = ?", companyId);
        jdbc.update("delete from deal where company_id = ?", companyId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where company_id = ?", companyId);   // 테스트가 심은 구성원 전부
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

    // ── 이달 성사 (DB-02) · 담당자별 실적 (DB-06) — #216

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 이번 달 안의 KST 시각 하나 — 1일 0시라 월 경계 자체도 이 값으로 시험된다 */
    private static OffsetDateTime 이달_1일_0시() {
        return YearMonth.now(SEOUL).atDay(1).atStartOfDay(SEOUL).toOffsetDateTime();
    }

    private static OffsetDateTime 지난달_마지막_순간() {
        return YearMonth.now(SEOUL).atDay(1).atStartOfDay(SEOUL).minusSeconds(1).toOffsetDateTime();
    }

    @Test
    @DisplayName("이달 성사는 주문 합계다 — 관리자는 회사 전체를 본다 (DB-02, DL-18)")
    void 이달_성사_관리자() {
        전환된_주문(딜(박지훈, "WON", null, null), 1_100_000L, 이달_1일_0시());
        전환된_주문(딜(다른영업, "WON", null, null), 2_200_000L, 이달_1일_0시());

        WonStats won = salesStatsQuery.monthlyWon(
                new AccessContext(companyId, 박지훈, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL),
                YearMonth.now(SEOUL));

        assertThat(won.amount()).isEqualTo(3_300_000L);
        assertThat(won.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("영업은 담당 딜의 주문만 집계된다 — 남의 성사가 섞이면 SC-04가 뚫린다")
    void 이달_성사_영업() {
        전환된_주문(딜(박지훈, "WON", null, null), 1_100_000L, 이달_1일_0시());
        전환된_주문(딜(다른영업, "WON", null, null), 2_200_000L, 이달_1일_0시());

        WonStats won = salesStatsQuery.monthlyWon(
                new AccessContext(companyId, 박지훈, Role.SALES_REP, AccessScope.OWNED_ONLY),
                YearMonth.now(SEOUL));

        assertThat(won.amount()).isEqualTo(1_100_000L);
        assertThat(won.count()).isEqualTo(1);
    }

    /**
     * <b>이 테스트가 SC-04의 마지막 방어선이다.</b> 담당 딜이 하나도 없는 영업의 견적 목록은
     * <b>빈 목록</b>인데, 그것을 "제한 없음(null)"으로 흘리면 회사 전체 성사액이 그대로 보인다.
     * 빈 목록과 null이 뒤집히는 사고는 조건 조립 한 줄만 바꿔도 일어난다.
     */
    @Test
    @DisplayName("담당 딜이 없는 영업은 0이다 — 빈 목록이 '전부'로 뒤집히면 안 된다 (SC-04)")
    void 이달_성사_담당딜_없는_영업() {
        전환된_주문(딜(다른영업, "WON", null, null), 2_200_000L, 이달_1일_0시());
        UUID 딜_없는_영업 = UUID.randomUUID();
        구성원(딜_없는_영업, "최민수");

        WonStats won = salesStatsQuery.monthlyWon(
                new AccessContext(companyId, 딜_없는_영업, Role.SALES_REP, AccessScope.OWNED_ONLY),
                YearMonth.now(SEOUL));

        assertThat(won.amount()).isZero();     // null이 아니라 0이다 (#85 D 합의)
        assertThat(won.count()).isZero();
    }

    /**
     * 월 경계는 <b>한국 날짜</b>로 끊는다. 서버 시간대로 끊으면 자정 부근 전환이 옆 달로 새는데,
     * 월말 마감 화면에서 바로 드러나는 종류의 오차다.
     */
    @Test
    @DisplayName("지난달 마지막 순간의 전환은 이달에 잡히지 않는다 — 경계는 KST다")
    void 이달_성사_월경계() {
        UUID deal = 딜(박지훈, "WON", null, null);
        전환된_주문(deal, 5_000_000L, 지난달_마지막_순간());
        전환된_주문(딜(박지훈, "WON", null, null), 1_100_000L, 이달_1일_0시());

        WonStats won = salesStatsQuery.monthlyWon(
                new AccessContext(companyId, 박지훈, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL),
                YearMonth.now(SEOUL));

        assertThat(won.amount()).isEqualTo(1_100_000L);   // 1일 0시는 포함, 그 1초 전은 제외
        assertThat(won.count()).isEqualTo(1);
    }

    /**
     * 실적이 담당자에게 제대로 귀속되는지 본다 — 주문에는 담당자 컬럼이 없어
     * {@code quote → deal → assignee} 두 홉을 거친다. 한 홉이라도 어긋나면 남의 실적이 붙는다.
     *
     * <p>{@code activeDealCount}는 <b>기간과 무관한 현재 스냅샷</b>이다 (D 확정) —
     * 시드의 진행 중 딜(LEAD 2 · QUOTE 1)이 박지훈에게 3건 잡힌다. 소프트 삭제된 CONSULT는 빠진다.
     */
    @Test
    @DisplayName("담당자별 실적 — 주문이 quote·deal을 거쳐 담당자에게 귀속된다 (DB-06)")
    void 담당자별_실적() {
        전환된_주문(딜(박지훈, "WON", null, null), 1_100_000L, 이달_1일_0시());
        전환된_주문(딜(박지훈, "WON", null, null), 2_200_000L, 이달_1일_0시());
        전환된_주문(딜(다른영업, "WON", null, null), 500_000L, 이달_1일_0시());

        List<MemberPerformance> rows = salesStatsQuery.performance(
                companyId, LocalDate.now(SEOUL).withDayOfMonth(1), LocalDate.now(SEOUL));

        assertThat(rows).extracting(MemberPerformance::name)
                .containsExactly("박지훈", "이서준");   // 성사 금액 내림차순

        assertThat(rows.getFirst().wonAmount()).isEqualTo(3_300_000L);
        assertThat(rows.getFirst().wonCount()).isEqualTo(2);
        assertThat(rows.getFirst().activeDealCount()).isEqualTo(3);   // 기간 밖 현재값 (LEAD 2 · QUOTE 1)

        assertThat(rows.getLast().wonAmount()).isEqualTo(500_000L);
        assertThat(rows.getLast().activeDealCount()).isEqualTo(1);    // 다른영업의 LEAD 1건
    }

    /**
     * 실적이 없는 구성원도 0으로 선다 — 빠지면 화면에서 "아직 집계 안 됨"과 구별되지 않는다.
     */
    @Test
    @DisplayName("성사가 없는 구성원도 0으로 목록에 선다")
    void 실적_없는_구성원도_0으로_선다() {
        List<MemberPerformance> rows = salesStatsQuery.performance(
                companyId, LocalDate.now(SEOUL).minusMonths(1), LocalDate.now(SEOUL));

        assertThat(rows).extracting(MemberPerformance::name)
                .containsExactlyInAnyOrder("박지훈", "이서준");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.wonCount()).isZero();
            assertThat(row.wonAmount()).isZero();   // null이 아니다 (#85)
        });
    }

    /**
     * <b>비활성 구성원의 실적은 사라지면 안 된다.</b> 비활성화가 이관하는 것은 <b>진행 중</b> 딜뿐이라
     * (MB-14) 성사된 딜은 떠난 담당자에게 남는다. 활성 목록만 추리면 그 금액이 {@code performance}에서
     * 빠져 같은 화면의 {@code monthlyWon} 총액과 어긋난다 (2026-09-10 D 확인).
     *
     * <p>이 분기만 {@code MemberQuery.get}으로 이름을 되짚는다 — 활성 목록에 없는 id라서다.
     */
    @Test
    @DisplayName("비활성 구성원도 실적이 있으면 목록에 선다 — 총액이 monthlyWon과 어긋나지 않는다")
    void 비활성_구성원_실적() {
        UUID 퇴사자 = UUID.randomUUID();
        구성원(퇴사자, "정우성");
        jdbc.update("update member set status = 'INACTIVE' where id = ?", 퇴사자);
        전환된_주문(딜(퇴사자, "WON", null, null), 7_000_000L, 이달_1일_0시());

        List<MemberPerformance> rows = salesStatsQuery.performance(
                companyId, LocalDate.now(SEOUL).withDayOfMonth(1), LocalDate.now(SEOUL));

        assertThat(rows).extracting(MemberPerformance::name).contains("정우성");
        assertThat(rows).filteredOn(row -> row.name().equals("정우성"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.wonAmount()).isEqualTo(7_000_000L);
                    assertThat(row.activeDealCount()).isZero();   // 진행 중 딜은 이관돼 남지 않는다
                });

        // performance 합계가 monthlyWon과 같아야 한다 — 이 테스트의 핵심이다
        long 합계 = rows.stream().mapToLong(MemberPerformance::wonAmount).sum();
        WonStats won = salesStatsQuery.monthlyWon(
                new AccessContext(companyId, 박지훈, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL),
                YearMonth.now(SEOUL));
        assertThat(합계).isEqualTo(won.amount());
    }
}
