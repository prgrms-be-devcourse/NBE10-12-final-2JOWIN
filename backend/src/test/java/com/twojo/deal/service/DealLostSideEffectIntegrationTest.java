package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.QuoteCommand;
import com.twojo.boundary.Role;
import com.twojo.deal.dto.DealRequests;
import com.twojo.global.error.BusinessException;
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
 * 딜 실패 처리의 <b>부수효과</b>를 실 DB로 고정한다 (DL-10, 전이표 §5, #318).
 *
 * <p>요구사항이 "실패 처리하면 진행 중이던 견적과 열람 링크는 만료된다"이고, 전이표 105행이
 * 그 결과로 <b>"실패 처리 시 견적·링크가 이미 만료되어 승인 경로가 닫힌다"</b>를 적는다.
 * 이 클래스가 보는 것은 그 마지막 문장이다 — <b>고객 승인이 실제로 막히는가</b>.
 *
 * <p>단위 테스트({@code DealServiceTest}·{@code QuoteCommandImplTest})는 창구를 부르는지와
 * 무엇을 닫는지까지만 본다. 커밋된 뒤에도 그런지, 그 상태에서 고객 경로가 닫히는지는
 * 실제 트랜잭션에서만 드러난다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다</b> — 붙이면 실패 처리가 테스트 트랜잭션에 합류해
 * 커밋 경계가 사라진다. 뒷정리는 {@code @AfterEach}에서 직접 한다
 * ({@code QuoteExpiryIntegrationTest}와 같은 이유).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DealLostSideEffectIntegrationTest {

    @Autowired
    private DealService dealService;
    @Autowired
    private QuoteCommand quoteCommand;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID contactId;
    private UUID dealId;
    private AccessContext ctx;

    @BeforeEach
    void 회사와_딜을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')", companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')",
                memberId, companyId, "sales-" + memberId + "@twojo.test", "박지훈");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "도담산업");
        jdbc.update("insert into customer_contact (id, customer_id, name, email, is_primary) "
                        + "values (?, ?, '이수정', ?, true)",
                contactId, customerId, "sujeong-" + contactId + "@dodam.test");
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'NEGOTIATION', 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from quote_view_token where quote_id in (select id from quote where company_id = ?)",
                companyId);
        jdbc.update("delete from quote where company_id = ?", companyId);
        jdbc.update("delete from audit_log where company_id = ?", companyId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer_contact where customer_id = ?", customerId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /** 발송된 견적 하나 + 살아 있는 열람 링크 하나 */
    private UUID 발송된_견적(String status) {
        UUID quoteId = UUID.randomUUID();
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, sent_at, version) "
                        + "values (?, ?, ?, ?, ?, 'EXCLUDED', 300000, 30000, 330000, ?, now(), 0)",
                quoteId, companyId, dealId, "Q-" + quoteId.toString().substring(0, 8), status,
                LocalDate.now().plusDays(30));
        jdbc.update("insert into quote_view_token (id, quote_id, recipient_contact_id, token_hash, status, expires_at) "
                        + "values (?, ?, ?, ?, 'ACTIVE', now() + interval '30 days')",
                UUID.randomUUID(), quoteId, contactId, quoteId.toString().replace("-", "") + "aa");
        return quoteId;
    }

    private String quoteStatus(UUID quoteId) {
        return jdbc.queryForObject("select status from quote where id = ?", String.class, quoteId);
    }

    private String tokenStatus(UUID quoteId) {
        return jdbc.queryForObject("select status from quote_view_token where quote_id = ?", String.class, quoteId);
    }

    private String tokenReason(UUID quoteId) {
        return jdbc.queryForObject("select expired_reason from quote_view_token where quote_id = ?",
                String.class, quoteId);
    }

    private void 실패처리() {
        dealService.lose(ctx, dealId, new DealRequests.LoseDeal("경쟁사 선정", 0));
    }

    /**
     * <b>이 테스트가 이 이슈의 핵심이다.</b> 전이표 105행이 "승인 경로가 닫힌다"고 규정하는데,
     * 그 차단은 견적이 EXPIRED가 되어야만 일어난다 — 승인은 견적 상태만 보기 때문이다
     * ({@code QuoteCommandImpl.approve}는 딜 상태를 보지 않는다).
     */
    @Test
    @DisplayName("실패 처리 뒤에는 고객이 승인할 수 없다 — 전이표 §5가 규정한 차단 (DL-10)")
    void 실패_뒤_고객_승인이_막힌다() {
        UUID quoteId = 발송된_견적("VIEWED");

        실패처리();

        assertThatThrownBy(() -> quoteCommand.approve(quoteId, new QuoteCommand.Responder("이수정", "구매팀장")))
                .isInstanceOf(BusinessException.class);
        assertThat(quoteStatus(quoteId)).isEqualTo("EXPIRED");   // 승인으로 바뀌지 않았다
    }

    /**
     * 딜 하나에 견적을 여러 건 만들 수 있다 (QT-18) — <b>전부</b> 닫혀야 한다.
     * 하나라도 남으면 그 링크로 승인할 수 있어 문제가 그대로다.
     */
    @Test
    @DisplayName("견적이 여럿이면 전부 닫고 링크도 DEAL_LOST로 만료된다 (QT-18)")
    void 견적이_여럿이어도_전부_닫힌다() {
        UUID 발송됨 = 발송된_견적("SENT");
        UUID 열람됨 = 발송된_견적("VIEWED");

        실패처리();

        assertThat(quoteStatus(발송됨)).isEqualTo("EXPIRED");
        assertThat(quoteStatus(열람됨)).isEqualTo("EXPIRED");
        assertThat(tokenStatus(발송됨)).isEqualTo("EXPIRED");
        assertThat(tokenStatus(열람됨)).isEqualTo("EXPIRED");
        assertThat(tokenReason(발송됨)).isEqualTo("DEAL_LOST");
        assertThat(tokenReason(열람됨)).isEqualTo("DEAL_LOST");
    }

    /**
     * <b>이미 응답이 끝난 견적은 건드리지 않는다.</b> 승인된 견적을 닫으면 주문 전환 경로가
     * 사라진다 — 딜이 실패했어도 그 견적은 "응답이 끝난 이력"이다 (전이표 §6).
     */
    @Test
    @DisplayName("승인된 견적은 닫지 않는다 — 진행 중인 것만 대상이다")
    void 응답_완료_견적은_남는다() {
        UUID 승인됨 = 발송된_견적("APPROVED");
        UUID 발송됨 = 발송된_견적("SENT");

        실패처리();

        assertThat(quoteStatus(승인됨)).isEqualTo("APPROVED");
        assertThat(tokenStatus(승인됨)).isEqualTo("ACTIVE");   // 링크도 그대로다
        assertThat(quoteStatus(발송됨)).isEqualTo("EXPIRED");
    }

    /** 견적을 만들지 않은 딜을 실패 처리하는 것은 정상이다 — 예외가 아니다 */
    @Test
    @DisplayName("견적 없는 딜도 실패 처리된다")
    void 견적_없는_딜() {
        실패처리();

        assertThat(jdbc.queryForObject("select stage from deal where id = ?", String.class, dealId))
                .isEqualTo("LOST");
    }
}
