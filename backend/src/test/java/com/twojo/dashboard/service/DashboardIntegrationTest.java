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
 *   <li>B 모듈({@code ActivityQuery}·{@code TaskQuery})은 #178로 실구현이라 시드한 활동·할 일이
 *       딜 제목까지 채워져 <b>실데이터</b>로 나온다 (목 테스트로는 JPQL 한 줄도 안 돈다).</li>
 *   <li>C 모듈({@code SalesStatsQuery}·{@code QuoteQuery.findAwaitingResponse})은 아직 throw 스텁이라
 *       서비스의 degrade가 걸려 <b>빈 값</b>으로 나온다. (#207 머지 후 {@code pipeline}은 실구현이
 *       되므로 그 단언은 그때 시드 딜 버킷 검증으로 교체한다.)</li>
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
    private UUID activityId;
    private UUID taskId;

    @BeforeEach
    void 한_회사_한_딜_한_활동_한_할일을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        repId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        dealId = UUID.randomUUID();
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
    @DisplayName("관리자 summary는 활동·할 일을 딜 제목까지 채워 실데이터로 주고, C 집계는 degrade로 빈 값이다")
    void 관리자_summary는_하이브리드로_나온다() {
        DashboardSummaryResponse res = dashboardService.summary(admin(), YearMonth.now());

        assertThat(res.recentActivities())
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.dealTitle()).isEqualTo("대시보드 시연 딜");
                    assertThat(a.summary()).contains("리모델링 일정 확인");
                    assertThat(a.dealId()).isEqualTo(dealId);
                });
        assertThat(res.followUps())
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.dealTitle()).isEqualTo("대시보드 시연 딜");
                    assertThat(f.content()).isEqualTo("재방문 예약");
                });

        // C degrade — #207 머지 후 pipeline은 실구현되므로 이 단언은 시드 딜(QUOTE) 버킷 검증으로 교체한다.
        assertThat(res.pipeline()).isEmpty();
        assertThat(res.monthWonAmount()).isEqualTo(0L);
        assertThat(res.monthWonCount()).isEqualTo(0);
        assertThat(res.waitingQuotes()).isEmpty();
    }

    @Test
    @DisplayName("영업 담당자 summary는 waitingQuotes가 강제 빈 목록이고, 본인 담당 딜의 활동·할 일은 나온다")
    void 영업담당자_summary는_응답대기가_비고_본인_담당은_나온다() {
        DashboardSummaryResponse res = dashboardService.summary(rep(), YearMonth.now());

        assertThat(res.waitingQuotes()).isEmpty();
        assertThat(res.recentActivities()).hasSize(1);
        assertThat(res.followUps()).hasSize(1);
    }

    @Test
    @DisplayName("관리자 performance는 degrade로 members·conversions가 빈 목록이다")
    void 관리자_performance는_빈_목록으로_degrade한다() {
        DashboardPerformanceResponse res =
                dashboardService.performance(admin(), LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(res.members()).isEmpty();
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
