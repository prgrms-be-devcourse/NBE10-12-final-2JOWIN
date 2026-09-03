package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.MailCommand;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.ViewTokenCommand;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 재발송 시 "견적당 활성 링크 1개 유지" (05 §7 · {@code uk_quote_view_token_active}).
 *
 * <p><b>목으로는 성립하지 않는다.</b> Hibernate는 flush할 때 INSERT를 UPDATE보다 먼저 내보낸다.
 * 만료 UPDATE와 발급 INSERT가 같은 flush에 묶이면 기존 행이 ACTIVE인 채로 새 ACTIVE가 들어가
 * 부분 유니크 인덱스에 걸린다. {@code issue()}의 {@code flush()} 한 줄을 지우면
 * {@code ViewTokenCommandImplTest}는 그대로 통과하고 이 테스트만 빨간불이 된다 —
 * 목 저장소엔 인덱스도 flush 순서도 없기 때문이다. A의 {@code PasswordResetIntegrationTest}
 * ({@code uk_password_reset_token_active})와 같은 메커니즘·같은 패턴이다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 {@code issue()}가 테스트 트랜잭션에
 * 합류해 커밋이 미뤄지고, 두 번째 호출이 확정 전 상태를 본다. {@code issue()}가 매 호출 스스로
 * 커밋해야 재발송 경로(첫 발급 커밋 → 재발급)가 재현된다.
 *
 * <p>{@code QuoteQuery.getPublicView}·{@code MailCommand.schedule}는 아직 스텁이라 {@link MockitoBean}으로
 * 세운다. 리포지토리와 PG만 실제다 — {@code contextLoads}가 이미 요구하는 인프라 그대로.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewTokenCommandIssueIntegrationTest {

    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 12, 31);

    @Autowired
    private ViewTokenCommand viewTokenCommand;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private QuoteQuery quoteQuery;
    @MockitoBean
    private MailCommand mailCommand;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID contactId;
    private UUID dealId;
    private UUID quoteId;

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

        jdbc.update("insert into application (id, company_name, business_no, email, status) "
                        + "values (?, ?, ?, ?, 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')",
                companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')",
                memberId, companyId, "sales-" + memberId + "@twojo.test", "박지훈");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "도담산업");
        jdbc.update("insert into customer_contact (id, customer_id, name, email) values (?, ?, ?, ?)",
                contactId, customerId, "이수정", "sujeong@dodam.co.kr");
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'LEAD', 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, 'DRAFT', 1000000, 100000, 1100000, ?, 0)",
                quoteId, companyId, dealId, "Q-TEST-001", VALID_UNTIL);

        given(quoteQuery.getPublicView(quoteId)).willReturn(new QuoteQuery.PublicQuoteView(
                quoteId, "Q-TEST-001", "DRAFT", "EXCLUDED", null, VALID_UNTIL,
                1_000_000L, 100_000L, 1_100_000L, List.of(), dealId, companyId));
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from quote_view_token where quote_id = ?", quoteId);
        jdbc.update("delete from quote where id = ?", quoteId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer_contact where id = ?", contactId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /** 05 §7 — 재발송 시 기존 활성 링크는 EXPIRED(RESENT), 새 활성 링크 1개만 남는다 */
    @Test
    void 재발송하면_활성_링크는_하나만_남고_이전_링크는_RESENT로_남는다() {
        // given — 최초 발송으로 활성 링크가 하나 있다
        viewTokenCommand.issue(quoteId, contactId);
        assertThat(개수("ACTIVE")).isEqualTo(1);

        // when — 같은 견적을 재발송하면 (flush 순서가 어긋나면 여기서 부분 유니크 위반)
        assertThatCode(() -> viewTokenCommand.issue(quoteId, contactId)).doesNotThrowAnyException();

        // then — 활성 1개 · 이전 링크는 RESENT 이력으로 남는다
        assertThat(개수("ACTIVE")).isEqualTo(1);
        assertThat(개수("EXPIRED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select expired_reason from quote_view_token where quote_id = ? and status = 'EXPIRED'",
                String.class, quoteId)).isEqualTo("RESENT");
    }

    /** JPA를 거치지 않고 원본 행을 센다 — 영속성 컨텍스트가 답을 대신 만들지 않게. */
    private Integer 개수(String status) {
        return jdbc.queryForObject(
                "select count(*) from quote_view_token where quote_id = ? and status = ?",
                Integer.class, quoteId, status);
    }
}
