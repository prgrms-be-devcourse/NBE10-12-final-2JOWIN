package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * NT-05 — 무응답 견적 리마인드 배치의 전 구간을 실 PG로 검증한다.
 *
 * <p>{@link RemindNoResponseBatch#run()}(오케스트레이터, 트랜잭션 없음) →
 * {@link RemindWorker#remind}({@code REQUIRES_NEW}, 멱등 가드 + 인앱 저장 + 병행 메일 예약) →
 * {@code notification}·{@code email_log} 행. 목 단위 테스트가 못 보는 트랜잭션 위상과 파생 쿼리를 여기서 본다.
 *
 * <p>클래스에 {@code @Transactional}을 붙이지 않는다 — 붙이면 worker의 {@code REQUIRES_NEW} 커밋이
 * 롤백돼 검증이 무의미해진다. {@code EmailSender}는 {@link MockitoBean}으로 세워 실제 SMTP를 타지 않게 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RemindNoResponseIntegrationTest {

    @Autowired
    private RemindNoResponseBatch batch;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private EmailSender emailSender;

    private UUID applicationId;
    private UUID companyId;
    private UUID assigneeId;
    private UUID adminId;
    private UUID customerId;
    private UUID contactId;
    private UUID dealId;
    private UUID quoteId;
    private String assigneeEmail;

    @BeforeEach
    void seed() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        assigneeEmail = "rep-" + assigneeId + "@twojo.test";

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
                values (?, ?, ?, '박지훈', '010-2000-0001', 'SALES_REP', 'ACTIVE')
                """, assigneeId, companyId, assigneeEmail);
        jdbc.update("""
                insert into member (id, company_id, email, name, phone, role, status)
                values (?, ?, ?, '김서연', '010-2000-0002', 'COMPANY_ADMIN', 'ACTIVE')
                """, adminId, companyId, "admin-" + adminId + "@twojo.test");
        jdbc.update("""
                insert into customer (id, company_id, created_by_member_id, name)
                values (?, ?, ?, '도담건설')
                """, customerId, companyId, assigneeId);
        jdbc.update("""
                insert into customer_contact (id, customer_id, name, email)
                values (?, ?, '이수정', 'sujeong@dodam.test')
                """, contactId, customerId);
        jdbc.update("""
                insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version)
                values (?, ?, ?, ?, '시연 딜', 'QUOTE', 0)
                """, dealId, companyId, customerId, assigneeId);
        jdbc.update("""
                insert into quote (id, company_id, deal_id, quote_no, status, vat_mode,
                                   supply_amount, vat_amount, total_amount, valid_until, sent_at, version)
                values (?, ?, ?, 'Q-NT05-001', 'SENT', 'EXCLUDED', 1000000, 100000, 1100000, ?, ?, 0)
                """, quoteId, companyId, dealId, LocalDate.now().plusDays(20),
                OffsetDateTime.now().minusDays(5));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from notification where company_id = ?", companyId);
        jdbc.update("delete from email_log where template_type = 'QUOTE_REMIND' and company_id = ?", companyId);
        jdbc.update("delete from notification_setting where member_id in (?, ?)", assigneeId, adminId);
        jdbc.update("delete from quote where id = ?", quoteId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer_contact where id = ?", contactId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id in (?, ?)", assigneeId, adminId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    private List<Map<String, Object>> notifications() {
        return jdbc.queryForList(
                "select recipient_member_id, type, ref_type, ref_id, message from notification where company_id = ?",
                companyId);
    }

    private int remindEmailCount(String recipient) {
        Integer n = jdbc.queryForObject(
                "select count(*) from email_log where template_type = 'QUOTE_REMIND' and recipient_email = ?",
                Integer.class, recipient);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("발송 후 임계일수가 지난 무응답 견적 - 담당자 인앱 알림 + 병행 메일 예약")
    void 정상_리마인드() {
        batch.run();

        assertThat(notifications()).singleElement().satisfies(r -> {
            assertThat(r.get("recipient_member_id")).isEqualTo(assigneeId);
            assertThat(r.get("type")).isEqualTo("REMIND_NO_RESPONSE");
            assertThat(r.get("ref_type")).isEqualTo("QUOTE");
            assertThat(r.get("ref_id")).isEqualTo(quoteId);
            assertThat((String) r.get("message")).contains("Q-NT05-001");
        });
        assertThat(remindEmailCount(assigneeEmail)).isEqualTo(1);
    }

    @Test
    @DisplayName("배치를 두 번 돌려도 알림·메일은 각각 1건 (견적당 1회)")
    void 재실행해도_멱등() {
        batch.run();
        batch.run();

        assertThat(notifications()).hasSize(1);
        assertThat(remindEmailCount(assigneeEmail)).isEqualTo(1);
    }

    @Test
    @DisplayName("회사가 정지되면 리마인드하지 않는다 (Q-27)")
    void 정지_회사는_억제() {
        jdbc.update("update company set status = 'SUSPENDED' where id = ?", companyId);

        batch.run();

        assertThat(notifications()).isEmpty();
        assertThat(remindEmailCount(assigneeEmail)).isZero();
    }

    @Test
    @DisplayName("담당자가 NT-07 리마인드 수신을 끄면 인앱은 오되 메일은 예약하지 않는다")
    void 수신_설정_OFF면_메일_생략() {
        jdbc.update("""
                insert into notification_setting (id, member_id, type, enabled)
                values (?, ?, 'REMIND_NO_RESPONSE', false)
                """, UUID.randomUUID(), assigneeId);

        batch.run();

        assertThat(notifications()).hasSize(1);
        assertThat(remindEmailCount(assigneeEmail)).isZero();
    }

    @Test
    @DisplayName("발송 후 임계일수에 못 미친 견적은 리마인드하지 않는다")
    void 임계_미달_견적은_제외() {
        jdbc.update("update quote set sent_at = ? where id = ?",
                OffsetDateTime.now().minusDays(1), quoteId);

        batch.run();

        assertThat(notifications()).isEmpty();
        assertThat(remindEmailCount(assigneeEmail)).isZero();
    }
}
