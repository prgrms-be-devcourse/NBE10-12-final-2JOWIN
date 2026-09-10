package com.twojo.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.dashboard.dto.DashboardPerformanceResponse;
import com.twojo.dashboard.dto.DashboardSummaryResponse;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
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
 * 대시보드 조립을 실 DB·실 빈으로 검증한다 — <b>하이브리드</b>다.
 *
 * <ul>
 *   <li>B의 {@code ActivityQuery}·{@code TaskQuery}(#178), C의 {@code SalesStatsQuery.pipeline}·
 *       {@code QuoteQuery.findAwaitingResponse}(#207)는 실구현이라 시드한 딜·견적·활동·할 일이
 *       실 JPQL로 조회돼 나온다 (목 테스트로는 쿼리 한 줄도 안 돈다).</li>
 *   <li>C의 {@code monthlyWon}·{@code performance}는 실구현이다 (#216) — 이 시드에는 주문이 없어
 *       금액이 0으로 나오지만 <b>자리표시자가 아니라 실제 집계 결과</b>다.</li>
 *   <li>C의 {@code conversions}만 아직 자리표시자라 빈 목록이다 — 화면은 "0%"가 아니라
 *       "집계 준비 중"으로 표시한다.</li>
 * </ul>
 *
 * <p>클래스에 {@code @Transactional}을 붙이지 않는다 — 붙이면 커밋이 미뤄져 JdbcTemplate이 확정 전
 * 상태를 읽고, 조립기가 자체 readOnly 트랜잭션을 여는 설계와도 어긋난다 (MeServiceIntegrationTest와 같은 이유).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DashboardIntegrationTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID adminId;
    private UUID repId;
    private UUID customerId;
    private UUID contactId;
    private UUID dealId;
    private UUID quoteId;
    private UUID activityId;
    private UUID taskId;

    @BeforeEach
    void 한_회사_한_딜_한_견적_한_활동_한_할일을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        repId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        activityId = UUID.randomUUID();
        taskId = UUID.randomUUID();

        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("""
                insert into application (id, company_name, business_no, email, applicant_name, status)
                values (?, '한빛오피스', ?, ?, '김서연', 'APPROVED')
                """, applicationId, businessNo, "admin-" + adminId + "@twojo.test");
        jdbc.update("""
                insert into company (id, application_id, name, business_no, status)
                values (?, ?, '한빛오피스', ?, 'ACTIVE')
                """, companyId, applicationId, businessNo);
        jdbc.update("""
                insert into member (id, company_id, email, name, phone, role, status)
                values (?, ?, ?, '김서연', '010-2000-0001', 'COMPANY_ADMIN', 'ACTIVE')
                """, adminId, companyId, "admin-" + adminId + "@twojo.test");
        jdbc.update("""
                insert into member (id, company_id, email, name, phone, role, status)
                values (?, ?, ?, '박지훈', '010-2000-0002', 'SALES_REP', 'ACTIVE')
                """, repId, companyId, "rep-" + repId + "@twojo.test");
        jdbc.update("""
                insert into customer (id, company_id, created_by_member_id, name)
                values (?, ?, ?, '도담건설')
                """, customerId, companyId, repId);
        jdbc.update("""
                insert into customer_contact (id, customer_id, name, email)
                values (?, ?, '이수정', 'sujeong@dodam.test')
                """, contactId, customerId);
        jdbc.update("""
                insert into deal (id, company_id, customer_id, assignee_member_id, title, stage,
                                  expected_amount, due_date, version)
                values (?, ?, ?, ?, '대시보드 시연 딜', 'QUOTE', 5000000, ?, 0)
                """, dealId, companyId, customerId, repId, LocalDate.now().plusDays(30));
        jdbc.update("""
                insert into quote (id, company_id, deal_id, quote_no, status, vat_mode,
                                   supply_amount, vat_amount, total_amount, valid_until, sent_at, version)
                values (?, ?, ?, 'Q-INT-001', 'SENT', 'EXCLUDED', 1000000, 100000, 1100000, ?, ?, 0)
                """, quoteId, companyId, dealId, LocalDate.now().plusDays(30), OffsetDateTime.now().minusDays(2));
        jdbc.update("""
                insert into activity (id, company_id, deal_id, author_member_id, channel, content, occurred_at)
                values (?, ?, ?, ?, 'CALL', '리모델링 일정 확인', ?)
                """, activityId, companyId, dealId, repId, OffsetDateTime.now());
        jdbc.update("""
                insert into task (id, company_id, deal_id, content, due_date)
                values (?, ?, ?, '재방문 예약', ?)
                """, taskId, companyId, dealId, LocalDate.now().plusDays(3));
    }

    @AfterEach
    void 역순으로_지운다() {
        jdbc.update("delete from task where id = ?", taskId);
        jdbc.update("delete from activity where id = ?", activityId);
        jdbc.update("delete from quote where id = ?", quoteId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer_contact where id = ?", contactId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", repId);
        jdbc.update("delete from member where id = ?", adminId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    private AccessContext admin() {
        return new AccessContext(companyId, adminId, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    }

    private AccessContext rep() {
        return new AccessContext(companyId, repId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    @Test
    @DisplayName("관리자 summary — 파이프라인·응답 대기·활동·할 일·이달 성사가 실데이터")
    void 관리자_summary는_실데이터로_나온다() {
        DashboardSummaryResponse res = dashboardService.summary(admin(), YearMonth.now());

        // DB-01 — 시드 딜(QUOTE 단계)이 QUOTE 버킷에 1건, 나머지 단계는 0으로 채워짐
        assertThat(res.pipeline())
                .filteredOn(s -> s.stage().equals("QUOTE"))
                .singleElement()
                .satisfies(s -> assertThat(s.count()).isEqualTo(1));

        // DB-03 — 시드한 SENT 견적. customerName은 계약상 아직 null (B 창구 대기)
        assertThat(res.waitingQuotes())
                .singleElement()
                .satisfies(w -> {
                    assertThat(w.quoteNo()).isEqualTo("Q-INT-001");
                    assertThat(w.customerName()).isNull();
                });

        // DB-04·05 — 딜 제목까지 조립
        assertThat(res.recentActivities())
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.dealTitle()).isEqualTo("대시보드 시연 딜");
                    assertThat(a.summary()).contains("리모델링 일정 확인");
                });
        assertThat(res.followUps())
                .singleElement()
                .satisfies(f -> assertThat(f.content()).isEqualTo("재방문 예약"));

        // DB-02 — 실집계. 이 시드에는 전환된 주문이 없어 0이다 (자리표시자가 아니다)
        assertThat(res.monthWonAmount()).isEqualTo(0L);
        assertThat(res.monthWonCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("영업 담당자 summary — 본인 담당 딜의 응답 대기·활동·할 일이 나온다 (SC-02)")
    void 영업담당자_summary는_본인_담당만_나온다() {
        DashboardSummaryResponse res = dashboardService.summary(rep(), YearMonth.now());

        assertThat(res.waitingQuotes())
                .extracting(DashboardSummaryResponse.WaitingQuote::quoteNo)
                .containsExactly("Q-INT-001");
        assertThat(res.recentActivities()).hasSize(1);
        assertThat(res.followUps()).hasSize(1);
    }

    /**
     * {@code members}는 실구현이 됐다 (#216) — 활성 구성원이 전부 서고, 실적이 없으면 0으로 채워진다.
     * {@code activeDealCount}는 기간과 무관한 현재 스냅샷이라, 시드의 QUOTE 단계 딜이 담당자에게 1건 잡힌다.
     *
     * <p>{@code conversions}만 아직 빈 목록이다 — 전이 이력(audit_log STAGE_MOVED) 적재가 선행이다.
     */
    @Test
    @DisplayName("관리자 performance — members는 실집계, conversions만 자리표시자로 빈 목록")
    void 관리자_performance는_구성원별로_나온다() {
        DashboardPerformanceResponse res =
                dashboardService.performance(admin(), LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(res.members())
                .extracting(DashboardPerformanceResponse.MemberPerformance::name)
                .containsExactlyInAnyOrder("김서연", "박지훈");
        assertThat(res.members())
                .filteredOn(m -> m.name().equals("박지훈"))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.wonCount()).isZero();
                    assertThat(m.wonAmount()).isZero();      // 0건이어도 null이 아니다 (#85)
                    assertThat(m.activeDealCount()).isEqualTo(1);   // 시드의 QUOTE 단계 딜
                });

        assertThat(res.conversions()).isEmpty();
    }

    @Test
    @DisplayName("영업 담당자가 performance를 부르면 403 FORBIDDEN이다")
    void 영업담당자의_performance는_FORBIDDEN이다() {
        assertThatThrownBy(() ->
                dashboardService.performance(rep(), LocalDate.now().minusMonths(1), LocalDate.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
