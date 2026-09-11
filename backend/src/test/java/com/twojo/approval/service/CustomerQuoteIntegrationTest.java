package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.approval.dto.ApproveQuoteRequest;
import com.twojo.approval.dto.CreateInquiryRequest;
import com.twojo.approval.dto.RejectQuoteRequest;
import com.twojo.approval.token.TokenGenerator;
import com.twojo.boundary.PublicQuoteResponse;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
 * 실 PG로만 잡히는 것 (#105) — C의 {@code QuoteQuery.getPublicView}·{@code QuoteCommand} 실구현과
 * D의 {@code NotificationCommand} 실구현까지 관통한다.
 *
 * <ul>
 *   <li>GET 첫 열람이 {@code quote.first_viewed_at}·{@code status}를 <b>실제로 커밋</b>한다 —
 *       {@code view}가 무트랜잭션이고 첫 열람 부수효과만 {@link FirstViewRecorder}의 자체
 *       read-write 트랜잭션으로 도는데, 그게 커밋돼야 raw JDBC 조회에 보인다.</li>
 *   <li>승인·반려가 견적 상태 전이 + 응답자 기록 + 토큰 소진 + NT-04를 한 트랜잭션으로 남긴다.</li>
 *   <li>문의가 {@code customer_inquiry} + NT-10을 남기고, 정지 회사면 아무 것도 남기지 않는다.</li>
 *   <li>만료·응답완료·없는 토큰이 각각 410·409·404로 걸러지고 부수효과가 없다.</li>
 * </ul>
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 서비스가 테스트 트랜잭션에 합류해 커밋이
 * 미뤄지고 {@link JdbcTemplate}이 확정 전 상태를 읽는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CustomerQuoteIntegrationTest {

    private static final LocalDate VALID_UNTIL = LocalDate.now().plusDays(30);

    @Autowired
    private CustomerQuoteService customerQuoteService;
    @Autowired
    private JdbcTemplate jdbc;

    private final TokenGenerator tokenGenerator = new TokenGenerator();

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID contactId;
    private UUID dealId;
    private UUID quoteId;
    private String rawToken;

    @BeforeEach
    void 견적_체인을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')",
                companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, '박지훈', 'SALES_REP', 'ACTIVE')",
                memberId, companyId, "sales-" + memberId + "@twojo.test");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "도담산업");
        jdbc.update("insert into customer_contact (id, customer_id, name, email) values (?, ?, ?, ?)",
                contactId, customerId, "이수정", "sujeong@dodam.co.kr");
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, '도담 사무가구', 'LEAD', 0)",
                dealId, companyId, customerId, memberId);
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, terms, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, 'Q-TEST-001', 'SENT', 'EXCLUDED', '설치는 납품일로부터 3일 이내', "
                        + "1000000, 100000, 1100000, ?, 0)",
                quoteId, companyId, dealId, VALID_UNTIL);
        jdbc.update("insert into quote_item (id, quote_id, name, unit, quantity, unit_price, amount, sort_order) "
                        + "values (?, ?, '책상', '개', 2, 300000, 600000, 0)",
                UUID.randomUUID(), quoteId);
        jdbc.update("insert into quote_item (id, quote_id, name, unit, quantity, unit_price, amount, sort_order) "
                        + "values (?, ?, '의자', '개', 4, 100000, 400000, 1)",
                UUID.randomUUID(), quoteId);

        rawToken = tokenGenerator.generate();
        insertToken("ACTIVE", Timestamp.from(Instant.now().plusSeconds(86_400)));
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from notification where company_id = ?", companyId);
        jdbc.update("delete from customer_inquiry where quote_id = ?", quoteId);
        jdbc.update("delete from quote_view_token where quote_id = ?", quoteId);
        jdbc.update("delete from quote_item where quote_id = ?", quoteId);
        jdbc.update("delete from quote where id = ?", quoteId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer_contact where id = ?", contactId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        // 감사 로그는 리스너가 만든 행이다 — 회사보다 먼저 지운다 (#287)
        jdbc.update("delete from audit_log where company_id = ?", companyId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    // ─────────────── 열람 ───────────────

    @Test
    @DisplayName("GET 첫 열람 — first_viewed_at·status(VIEWED)가 PG에 커밋되고 NT-03 알림이 남는다")
    void 첫_열람이_PG에_커밋된다() {
        PublicQuoteResponse res = customerQuoteService.view(rawToken, Instant.now());

        assertThat(res.status()).isEqualTo("VIEWED");
        assertThat(res.respondable()).isTrue();
        assertThat(res.companyName()).isEqualTo("한빛오피스");
        assertThat(res.assignee().name()).isEqualTo("박지훈");
        assertThat(res.items()).extracting(PublicQuoteResponse.ItemView::name)
                .containsExactly("책상", "의자");

        assertThat(quoteColumn("status", String.class)).isEqualTo("VIEWED");
        assertThat(jdbc.queryForObject(
                "select first_viewed_at from quote where id = ?", Timestamp.class, quoteId)).isNotNull();
        assertThat(notificationCount("QUOTE_VIEWED")).isEqualTo(1);
    }

    @Test
    @DisplayName("재열람 — status는 VIEWED 유지, NT-03 알림이 늘지 않는다")
    void 재열람은_알림을_늘리지_않는다() {
        customerQuoteService.view(rawToken, Instant.now());
        customerQuoteService.view(rawToken, Instant.now());

        assertThat(quoteColumn("status", String.class)).isEqualTo("VIEWED");
        assertThat(notificationCount("QUOTE_VIEWED")).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 토큰이면 404 RESOURCE_NOT_FOUND")
    void 없는_토큰이면_404() {
        assertThatThrownBy(() -> customerQuoteService.view("does-not-exist", Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("만료된 토큰이면 410 LINK_EXPIRED, 견적 상태·알림에 손대지 않는다")
    void 만료_토큰이면_410_무부수효과() {
        jdbc.update("update quote_view_token set expires_at = ? where quote_id = ?",
                Timestamp.from(Instant.now().minusSeconds(3_600)), quoteId);

        assertThatThrownBy(() -> customerQuoteService.view(rawToken, Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_EXPIRED));

        assertThat(quoteColumn("status", String.class)).isEqualTo("SENT");
        assertThat(notificationCount("QUOTE_VIEWED")).isZero();
    }

    // ─────────────── 승인 · 반려 ───────────────

    @Test
    @DisplayName("승인 — 견적 APPROVED + 응답자 기록 + 토큰 RESPONDED + NT-04(QUOTE_APPROVED)")
    void 승인이_한_트랜잭션으로_남는다() {
        customerQuoteService.approve(rawToken, new ApproveQuoteRequest("이수정", "구매팀장"), Instant.now());

        assertThat(quoteColumn("status", String.class)).isEqualTo("APPROVED");
        assertThat(quoteColumn("responder_name", String.class)).isEqualTo("이수정");
        assertThat(quoteColumn("responder_title", String.class)).isEqualTo("구매팀장");
        assertThat(quoteColumn("responded_at", Timestamp.class)).isNotNull();
        assertThat(tokenStatus()).isEqualTo("RESPONDED");
        assertThat(notificationCount("QUOTE_APPROVED")).isEqualTo(1);
    }

    @Test
    @DisplayName("반려 — 견적 REJECTED + 사유 기록 + 토큰 RESPONDED + NT-04(QUOTE_REJECTED)")
    void 반려가_한_트랜잭션으로_남는다() {
        customerQuoteService.reject(rawToken,
                new RejectQuoteRequest("예산이 확보되지 않았습니다", "이수정", "구매팀장"), Instant.now());

        assertThat(quoteColumn("status", String.class)).isEqualTo("REJECTED");
        assertThat(quoteColumn("reject_reason", String.class)).isEqualTo("예산이 확보되지 않았습니다");
        assertThat(tokenStatus()).isEqualTo("RESPONDED");
        assertThat(notificationCount("QUOTE_REJECTED")).isEqualTo(1);
    }

    @Test
    @DisplayName("GET 없이 바로 승인 — markViewed 선행으로 성립하고 NT-03·NT-04가 모두 남는다")
    void GET_없이_승인해도_markViewed_선행으로_성립한다() {
        customerQuoteService.approve(rawToken, new ApproveQuoteRequest("이수정", null), Instant.now());

        assertThat(quoteColumn("status", String.class)).isEqualTo("APPROVED");
        assertThat(quoteColumn("first_viewed_at", Timestamp.class)).isNotNull();
        assertThat(notificationCount("QUOTE_VIEWED")).isEqualTo(1);
        assertThat(notificationCount("QUOTE_APPROVED")).isEqualTo(1);
    }

    @Test
    @DisplayName("RESPONDED 링크로 재응답하면 409 LINK_ALREADY_RESPONDED, 견적은 그대로")
    void RESPONDED_링크_재응답이면_409() {
        customerQuoteService.approve(rawToken, new ApproveQuoteRequest("이수정", null), Instant.now());

        assertThatThrownBy(() -> customerQuoteService.reject(rawToken,
                new RejectQuoteRequest("역시 취소", "이수정", null), Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_ALREADY_RESPONDED));
        assertThat(quoteColumn("status", String.class)).isEqualTo("APPROVED");
    }

    // ─────────────── 문의 ───────────────

    @Test
    @DisplayName("문의 — customer_inquiry 행 + NT-10(INQUIRY_RECEIVED)")
    void 문의가_기록되고_알림이_남는다() {
        customerQuoteService.createInquiry(rawToken,
                new CreateInquiryRequest("배송 일정을 알고 싶습니다"), Instant.now());

        assertThat(jdbc.queryForObject(
                "select content from customer_inquiry where quote_id = ?", String.class, quoteId))
                .isEqualTo("배송 일정을 알고 싶습니다");
        assertThat(notificationCount("INQUIRY_RECEIVED")).isEqualTo(1);
    }

    @Test
    @DisplayName("정지 회사 문의면 409 COMPANY_SUSPENDED, 문의·알림 아무 것도 안 남는다")
    void 정지_회사_문의면_409_무부수효과() {
        jdbc.update("update company set status = 'SUSPENDED' where id = ?", companyId);

        assertThatThrownBy(() -> customerQuoteService.createInquiry(rawToken,
                new CreateInquiryRequest("문의합니다"), Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMPANY_SUSPENDED));

        assertThat(jdbc.queryForObject(
                "select count(*) from customer_inquiry where quote_id = ?", Integer.class, quoteId)).isZero();
        assertThat(notificationCount("INQUIRY_RECEIVED")).isZero();
    }

    // ─────────────── 헬퍼 ───────────────

    private void insertToken(String status, Timestamp expiresAt) {
        jdbc.update("insert into quote_view_token (id, quote_id, recipient_contact_id, token_hash, status, expires_at) "
                        + "values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), quoteId, contactId, tokenGenerator.hash(rawToken), status, expiresAt);
    }

    private <T> T quoteColumn(String column, Class<T> type) {
        return jdbc.queryForObject("select " + column + " from quote where id = ?", type, quoteId);
    }

    private String tokenStatus() {
        return jdbc.queryForObject(
                "select status from quote_view_token where quote_id = ?", String.class, quoteId);
    }

    private Integer notificationCount(String type) {
        return jdbc.queryForObject(
                "select count(*) from notification where company_id = ? and recipient_member_id = ? and type = ?",
                Integer.class, companyId, memberId, type);
    }
}
