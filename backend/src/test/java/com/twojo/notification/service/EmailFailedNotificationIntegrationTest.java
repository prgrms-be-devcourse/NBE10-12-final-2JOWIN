package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;

import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MailCommand.TemplateType;
import java.time.Duration;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * NT-12 — 메일 최종 실패가 담당 구성원 인앱 {@code EMAIL_FAILED} 알림으로 이어지는 전 구간을 실 PG로 검증한다.
 *
 * <p>{@code schedule()} 커밋 → {@code @Async} 디스패처 발송 실패 →
 * {@link MailOutcomeWriter#markFailed}({@code REQUIRES_NEW}, SCHEDULED→FAILED 전이 + {@code EmailDeliveryFailedEvent}) →
 * {@link EmailFailedNotifier}(AFTER_COMMIT) → {@link EmailFailedNotificationWriter}({@code REQUIRES_NEW}) →
 * {@code notification} 행. 목 단위 테스트가 못 재현하는 트랜잭션 위상·스레드 홉을 여기서 본다.
 *
 * <p>클래스에 {@code @Transactional}을 붙이지 않는다 (붙이면 커밋이 미뤄져 AFTER_COMMIT이 안 뜬다).
 * {@code EmailSender}는 {@link MockitoBean}으로 세워 강제 실패시킨다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmailFailedNotificationIntegrationTest {

    @Autowired private MailCommand mailCommand;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;
    @MockitoBean private EmailSender emailSender;

    private TransactionTemplate tx;

    private UUID applicationId;
    private UUID companyId;
    private UUID assigneeId;
    private UUID adminId;
    private UUID customerId;
    private UUID contactId;
    private UUID dealId;
    private UUID quoteId;
    private UUID tokenId;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(txManager);
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        tokenId = UUID.randomUUID();

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
                """, assigneeId, companyId, "rep-" + assigneeId + "@twojo.test");
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
                values (?, ?, ?, 'Q-NT12-001', 'SENT', 'EXCLUDED', 1000000, 100000, 1100000, ?, ?, 0)
                """, quoteId, companyId, dealId, LocalDate.now().plusDays(20), OffsetDateTime.now().minusDays(1));
        jdbc.update("""
                insert into quote_view_token (id, quote_id, recipient_contact_id, token_hash, status, expires_at)
                values (?, ?, ?, ?, 'ACTIVE', ?)
                """, tokenId, quoteId, contactId, "hash-" + tokenId, OffsetDateTime.now().plusDays(20));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from notification where company_id = ?", companyId);
        jdbc.update("delete from email_log where recipient_email like 'nt12-%@test'");
        jdbc.update("delete from quote_view_token where id = ?", tokenId);
        jdbc.update("delete from quote where id = ?", quoteId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer_contact where id = ?", contactId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id in (?, ?)", assigneeId, adminId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    private void scheduleFailing(TemplateType type, UUID company, String recipient, UUID refId) {
        willThrow(new RuntimeException("SMTP down")).given(emailSender).send(eq(recipient), any(), any());
        tx.executeWithoutResult(t -> mailCommand.schedule(type, company, recipient, refId, "제목", "본문"));
    }

    private List<Map<String, Object>> notifications() {
        return jdbc.queryForList(
                "select recipient_member_id, type, ref_type, ref_id, message from notification where company_id = ?",
                companyId);
    }

    private String emailLogStatus(String recipient) {
        return jdbc.queryForObject(
                "select status from email_log where recipient_email = ?", String.class, recipient);
    }

    @Test
    @DisplayName("QUOTE_SENT 최종 실패 - 담당자에게 EMAIL_FAILED 인앱 알림 1건 (ref = 견적)")
    void QUOTE_SENT_실패면_담당자에게_알림() {
        scheduleFailing(TemplateType.QUOTE_SENT, companyId, "nt12-ok-" + tokenId + "@test", tokenId);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notifications()).singleElement().satisfies(r -> {
                    assertThat(r.get("recipient_member_id")).isEqualTo(assigneeId);
                    assertThat(r.get("type")).isEqualTo("EMAIL_FAILED");
                    assertThat(r.get("ref_type")).isEqualTo("QUOTE");
                    assertThat(r.get("ref_id")).isEqualTo(quoteId);
                    assertThat((String) r.get("message")).contains("Q-NT12-001");
                }));
    }

    @Test
    @DisplayName("담당자가 비활성이면 기업 관리자에게 EMAIL_FAILED (Q-26 폴백)")
    void 담당자_비활성이면_관리자에게() {
        jdbc.update("update member set status = 'INACTIVE' where id = ?", assigneeId);

        scheduleFailing(TemplateType.QUOTE_SENT, companyId, "nt12-fb-" + tokenId + "@test", tokenId);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notifications()).singleElement().satisfies(r -> {
                    assertThat(r.get("recipient_member_id")).isEqualTo(adminId);
                    assertThat(r.get("type")).isEqualTo("EMAIL_FAILED");
                }));
    }

    @Test
    @DisplayName("QUOTE_SENT가 아닌 실패는 인앱 알림을 만들지 않는다 (email_log FAILED만)")
    void 다른_template은_알림_없음() {
        String recipient = "nt12-other-" + UUID.randomUUID() + "@test";
        scheduleFailing(TemplateType.SIGNUP_APPROVED, null, recipient, UUID.randomUUID());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(emailLogStatus(recipient)).isEqualTo("FAILED"));
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(notifications()).isEmpty());
    }

    @Test
    @DisplayName("발송 토큰 행이 없으면 알림을 건너뛴다 (email_log는 FAILED로 남는다)")
    void 토큰_행이_없으면_알림_건너뜀() {
        String recipient = "nt12-notoken-" + UUID.randomUUID() + "@test";
        scheduleFailing(TemplateType.QUOTE_SENT, companyId, recipient, UUID.randomUUID());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(emailLogStatus(recipient)).isEqualTo("FAILED"));
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(notifications()).isEmpty());
    }
}
