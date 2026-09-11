package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.boundary.ViewTokenCommand;
import com.twojo.deal.dto.DealRequests;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 링크 만료가 실패하면 <b>실패 처리도 되돌아간다</b> (#318 완료 조건, #324 리뷰).
 *
 * <p>딜만 LOST이고 견적·링크가 열린 상태는 전이표에 없다 — 그게 남으면 이 이슈가 그대로 재현된다.
 *
 * <p><b>이 테스트가 고정하는 것은 "예외가 삼켜지지 않는다"이다.</b> 호출부가
 * {@code try/catch}로 감싸거나 구현이 실패를 무시하면 딜만 LOST로 남고 여기서 깨진다.
 *
 * <p><b>고정하지 <i>못하는</i> 것도 적어둔다</b> — {@code MANDATORY}와 {@code REQUIRES_NEW}를
 * 이 시나리오로는 가릴 수 없다. 링크 만료가 <b>던지면</b> 어느 전파 방식이든 예외가 위로 올라가
 * 바깥 트랜잭션까지 롤백되기 때문이다(실제로 {@code REQUIRES_NEW}로 바꿔도 통과한다).
 * 둘이 갈리는 자리는 링크 만료가 <b>성공한 뒤 바깥이 실패</b>하는 경우 — 그때
 * {@code REQUIRES_NEW}면 링크만 따로 커밋돼 남는다. 지금 {@code lose}에는 그 실패를 끼워 넣을
 * 지점이 없어 테스트로 만들지 못했다 (#324 리뷰).
 *
 * <p><b>클래스를 나눈 이유</b> — {@link ViewTokenCommand}를 {@link MockitoBean}으로 세우면 그 컨텍스트의
 * 모든 테스트가 가짜 링크를 보게 된다. {@code DealLostSideEffectIntegrationTest}는 토큰 행의 실제
 * 상태·사유를 단언하므로 같은 클래스에 둘 수 없다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 롤백을 보는 테스트라 커밋 경계가 진짜여야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DealLostRollbackIntegrationTest {

    @Autowired
    private DealService dealService;
    @Autowired
    private JdbcTemplate jdbc;

    /** 링크 만료만 실패시킨다 — 견적 전이는 실제로 일어난 뒤라 롤백 대상이 된다 */
    @MockitoBean
    private ViewTokenCommand viewTokenCommand;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID contactId;
    private UUID dealId;
    private UUID quoteId;
    private AccessContext ctx;

    @BeforeEach
    void 발송된_견적이_달린_딜을_심는다() {
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
                        + "values (?, ?, ?, ?, 'ACTIVE')", companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, '박지훈', 'SALES_REP', 'ACTIVE')",
                memberId, companyId, "sales-" + memberId + "@twojo.test");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "도담산업");
        jdbc.update("insert into customer_contact (id, customer_id, name, email, is_primary) "
                        + "values (?, ?, '이수정', ?, true)",
                contactId, customerId, "sujeong-" + contactId + "@dodam.test");
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, '도담 사무가구', 'NEGOTIATION', 0)",
                dealId, companyId, customerId, memberId);
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, sent_at, version) "
                        + "values (?, ?, ?, ?, 'VIEWED', 'EXCLUDED', 300000, 30000, 330000, ?, now(), 0)",
                quoteId, companyId, dealId, "Q-" + quoteId.toString().substring(0, 8),
                LocalDate.now().plusDays(30));

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from audit_log where company_id = ?", companyId);
        jdbc.update("delete from quote where company_id = ?", companyId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer_contact where customer_id = ?", customerId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    private String 단계() {
        return jdbc.queryForObject("select stage from deal where id = ?", String.class, dealId);
    }

    private String 견적상태() {
        return jdbc.queryForObject("select status from quote where id = ?", String.class, quoteId);
    }

    /**
     * <b>부분 성공이 남지 않는다.</b> 링크 만료가 던지면 딜 단계도 견적 상태도 실패 처리 이전으로
     * 돌아가야 한다 — 담당자에게는 오류가 보이고, 다시 시도하면 된다. 여기서 딜만 LOST로 남으면
     * 고객은 여전히 승인할 수 있는 링크를 들고 있는데 담당자는 실패로 알고 있게 된다.
     */
    @Test
    @DisplayName("링크 만료가 실패하면 딜 단계와 견적 상태가 함께 되돌아간다 — 예외를 삼키지 않는다")
    void 링크_만료_실패는_전부_되돌린다() {
        willThrow(new IllegalStateException("링크 만료 실패"))
                .given(viewTokenCommand).expire(any(), eq(ViewTokenCommand.ExpiredReason.DEAL_LOST));

        assertThatThrownBy(() -> dealService.lose(ctx, dealId, new DealRequests.LoseDeal("경쟁사 선정", 0)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(단계()).isEqualTo("NEGOTIATION");   // 실패 처리가 되돌아갔다
        assertThat(견적상태()).isEqualTo("VIEWED");     // 견적 전이도 함께 되돌아갔다
    }
}
