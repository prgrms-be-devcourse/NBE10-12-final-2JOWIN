package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;

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

/**
 * NT-06 — 유효기간 임박 알림 배치의 전 구간을 실 PG로 검증한다.
 *
 * <p>{@link ExpiringQuoteBatch#run()}(오케스트레이터, 트랜잭션 없음, {@code findExpiringBetween} 실구현
 * 소비) → {@link ExpiringQuoteWorker#remind}({@code REQUIRES_NEW}, 활성 토큰 조회 + 멱등 가드 +
 * {@code MailCommand.schedule}) → {@code email_log} 행. 실패 시에는 커밋 후 비동기 재시도 →
 * {@code MailOutcomeWriter}(FAILED 전이) → {@link EmailFailedNotifier} → 인앱 {@code EMAIL_FAILED}까지
 * 이어진다. 목 단위 테스트({@code ExpiringQuoteBatchTest})가 못 보는 실 쿼리 경계·트랜잭션 위상·
 * 비동기 다단 홉을 여기서 본다.
 *
 * <p>클래스에 {@code @Transactional}을 붙이지 않는다 — 붙이면 worker의 {@code REQUIRES_NEW} 커밋이
 * 테스트 트랜잭션에 말려 롤백되고, 실패 케이스의 AFTER_COMMIT 리스너도 뜨지 않는다.
 * {@code EmailSender}는 {@link MockitoBean}으로 세워 실제 SMTP를 타지 않게 한다.
 *
 * <p><b>1~5번은 즉시 assert하고 {@code email_log.status}는 보지 않는다.</b> 스킵 여부 판정(대상 상태·
 * 구간·정지 회사·활성 토큰·멱등)은 {@code schedule()}이 동기로 끝내는 부분이라 대기가 필요 없고,
 * SCHEDULED/SENT 전이는 비동기 타이밍에 따라 갈려 검증하면 플레이키해진다. <b>6번만</b> 발송 실패 →
 * 재시도 → FAILED 기록 → 이벤트 → 인앱 알림까지 다단 비동기라 {@code Awaitility}로 폴링한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExpiringQuoteIntegrationTest {

    @Autowired
    private ExpiringQuoteBatch batch;
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
    private UUID tokenId;
    private String contactEmail;

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
        tokenId = UUID.randomUUID();
        contactEmail = "sujeong-" + contactId + "@dodam.test";

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
                values (?, ?, '이수정', ?)
                """, contactId, customerId, contactEmail);
        jdbc.update("""
                insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version)
                values (?, ?, ?, ?, '시연 딜', 'QUOTE', 0)
                """, dealId, companyId, customerId, assigneeId);
        // valid_until = 오늘+1 — [오늘, 오늘+3] 구간 안 (application-test.yml before-days: 3)
        jdbc.update("""
                insert into quote (id, company_id, deal_id, quote_no, status, vat_mode,
                                   supply_amount, vat_amount, total_amount, valid_until, sent_at, version)
                values (?, ?, ?, 'Q-NT06-001', 'SENT', 'EXCLUDED', 1000000, 100000, 1100000, ?, ?, 0)
                """, quoteId, companyId, dealId, LocalDate.now().plusDays(1), OffsetDateTime.now().minusDays(1));
        jdbc.update("""
                insert into quote_view_token (id, quote_id, recipient_contact_id, token_hash, status, expires_at)
                values (?, ?, ?, ?, 'ACTIVE', ?)
                """, tokenId, quoteId, contactId, "hash-" + tokenId, OffsetDateTime.now().plusDays(1));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from notification where company_id = ?", companyId);
        jdbc.update("delete from email_log where template_type = 'QUOTE_EXPIRING' and company_id = ?", companyId);
        jdbc.update("delete from quote_view_token where id = ?", tokenId);
        jdbc.update("delete from quote where id = ?", quoteId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer_contact where id = ?", contactId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id in (?, ?)", assigneeId, adminId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    private List<Map<String, Object>> emailLogRows(String recipient) {
        return jdbc.queryForList("""
                select company_id, ref_type, ref_id from email_log
                where template_type = 'QUOTE_EXPIRING' and recipient_email = ?
                """, recipient);
    }

    private int emailLogCount(String recipient) {
        return emailLogRows(recipient).size();
    }

    private List<Map<String, Object>> notifications() {
        return jdbc.queryForList("""
                select recipient_member_id, type, ref_type, ref_id, message from notification
                where company_id = ?
                """, companyId);
    }

    @Test
    @DisplayName("구간 안의 미응답 견적 - 고객사 담당자에게 QUOTE_EXPIRING 메일을 예약한다")
    void 정상_예약() {
        batch.run();

        assertThat(emailLogRows(contactEmail)).singleElement().satisfies(r -> {
            assertThat(r.get("company_id")).isEqualTo(companyId);
            assertThat(r.get("ref_type")).isEqualTo("QUOTE");
            assertThat(r.get("ref_id")).isEqualTo(quoteId);
        });
    }

    @Test
    @DisplayName("배치를 두 번 돌려도 메일 예약은 1건이다 (견적당 1회)")
    void 재실행해도_멱등() {
        batch.run();
        batch.run();

        assertThat(emailLogCount(contactEmail)).isEqualTo(1);
    }

    @Test
    @DisplayName("회사가 정지되면 임박 메일을 예약하지 않는다 (Q-27)")
    void 정지_회사는_억제() {
        jdbc.update("update company set status = 'SUSPENDED' where id = ?", companyId);

        batch.run();

        assertThat(emailLogCount(contactEmail)).isZero();
    }

    @Test
    @DisplayName("이미 유효기간이 지난 견적은 대상이 아니다 (구간 하한 밖)")
    void 구간_하한_밖은_제외() {
        jdbc.update("update quote set valid_until = ? where id = ?", LocalDate.now().minusDays(1), quoteId);

        batch.run();

        assertThat(emailLogCount(contactEmail)).isZero();
    }

    @Test
    @DisplayName("아직 임박 기준일보다 여유 있는 견적은 대상이 아니다 (구간 상한 밖, before-days=3)")
    void 구간_상한_밖은_제외() {
        jdbc.update("update quote set valid_until = ? where id = ?", LocalDate.now().plusDays(4), quoteId);

        batch.run();

        assertThat(emailLogCount(contactEmail)).isZero();
    }

    @Test
    @DisplayName("활성 열람 토큰이 없으면 조용히 건너뛴다 (배치는 죽지 않는다)")
    void 활성_토큰_없으면_건너뜀() {
        jdbc.update("delete from quote_view_token where id = ?", tokenId);

        assertThatCode(() -> batch.run()).doesNotThrowAnyException();

        assertThat(emailLogCount(contactEmail)).isZero();
    }

    @Test
    @DisplayName("메일 발송이 끝내 실패하면 딜 담당자에게 인앱 EMAIL_FAILED가 관통한다")
    void 메일_강제_실패시_EMAIL_FAILED_관통() {
        willThrow(new RuntimeException("SMTP down")).given(emailSender).send(eq(contactEmail), any(), any());

        batch.run();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notifications()).singleElement().satisfies(r -> {
                    assertThat(r.get("recipient_member_id")).isEqualTo(assigneeId);
                    assertThat(r.get("type")).isEqualTo("EMAIL_FAILED");
                    assertThat(r.get("ref_type")).isEqualTo("QUOTE");
                    assertThat(r.get("ref_id")).isEqualTo(quoteId);
                    assertThat((String) r.get("message")).contains("Q-NT06-001");
                }));
    }
}
