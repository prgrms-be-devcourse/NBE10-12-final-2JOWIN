package com.twojo.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.DealCommand;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.Role;
import com.twojo.quote.dto.QuoteRequests;
import com.twojo.quote.service.QuoteService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 발송 트랜잭션의 원자성 — <b>C의 대표 Evidence</b> (Q-40, 전이표 §6).
 *
 * <p>발송 한 번에 <b>네 가지가 함께 일어난다</b>: 열람 링크 발급 · 안내 메일 예약 ·
 * Deal 단계 자동 승급 · 견적 SENT 전이. 전이표 §6이 링크 발급을 발송의 <b>효과</b>로
 * 규정하므로, 하나라도 어긋나면 표에 없는 상태가 남는다 —
 * "링크 없는 SENT 견적"이나 "SENT가 아닌데 링크가 살아 있는 견적" 같은 것이다.
 *
 * <p><b>목으로는 성립하지 않는다.</b> 여기서 보는 것은 "롤백이 실제로 네 가지를 함께
 * 되돌리는가"이고, 그건 커밋 경계가 있는 실제 트랜잭션에서만 재현된다.
 * 서비스를 목으로 감싸면 각 호출이 일어났는지만 알 수 있지 <b>되돌아갔는지</b>는 모른다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 발송이 테스트 트랜잭션에 합류해
 * 롤백 경계가 사라진다. {@code DealStageConcurrencyTest}와 같은 이유다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QuoteSendTransactionTest {

    @Autowired
    private QuoteService quoteService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 메일 예약을 가로챈다 — 실패를 <b>링크 저장 뒤</b>에 일으키는 자리다.
     *
     * <p>{@code ViewTokenCommandImpl.issue}가 토큰을 저장한 다음 마지막에 이걸 부른다.
     * 여기서 던지면 "링크는 만들어졌는데 그 뒤가 깨진" 상황이 정확히 재현된다.
     */
    @MockitoBean
    private MailCommand mailCommand;

    /**
     * 단계 승급을 가로챈다 — 실패를 <b>링크 발급이 끝난 뒤</b>로 옮기는 자리다.
     *
     * <p><b>{@code MailCommand}로는 이 검증이 성립하지 않는다.</b> 그건 {@code issue()}
     * <b>안에서</b> 불려서, 거기서 던지면 {@code issue()} 자신의 트랜잭션이 롤백되어
     * 바깥 트랜잭션이 없어도 링크가 사라진다 — 무엇을 검증하는지 알 수 없는 테스트가 된다.
     *
     * <p>{@code spy}라 기본은 실제 구현이 돈다 — 성공 경로에서 단계가 진짜로 올라가야 하기 때문이다.
     */
    @MockitoSpyBean
    private DealCommand dealCommand;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID contactId;
    private UUID dealId;
    private UUID quoteId;
    private AccessContext ctx;

    @BeforeEach
    void 발송_직전_상태를_심는다() {
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
                        + "values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')",
                memberId, companyId, "sales-" + memberId + "@twojo.test", "박지훈");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "도담산업");
        jdbc.update("insert into customer_contact (id, customer_id, name, email, is_primary) "
                        + "values (?, ?, ?, ?, true)",
                contactId, customerId, "이건우", "gunwoo-" + contactId + "@dodam.test");

        // 리드 단계 — 발송하면 견적(QUOTE)으로 자동 승급되어야 한다 (Q-25)
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'LEAD', 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, 'DRAFT', 'EXCLUDED', 300000, 30000, 330000, ?, 0)",
                quoteId, companyId, dealId, "Q-TEST-001", LocalDate.now().plusDays(15));
        jdbc.update("insert into quote_item (id, quote_id, name, unit, quantity, unit_price, amount, sort_order) "
                        + "values (?, ?, ?, ?, 1, 300000, 300000, 0)",
                UUID.randomUUID(), quoteId, "현장 실측 및 배치 설계", "식");

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from email_log where company_id = ?", companyId);
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

    private String quoteStatus() {
        return jdbc.queryForObject("select status from quote where id = ?", String.class, quoteId);
    }

    private String dealStage() {
        return jdbc.queryForObject("select stage from deal where id = ?", String.class, dealId);
    }

    private int quoteVersion() {
        return jdbc.queryForObject("select version from quote where id = ?", Integer.class, quoteId);
    }

    private int activeLinkCount() {
        return jdbc.queryForObject(
                "select count(*) from quote_view_token where quote_id = ? and status = 'ACTIVE'",
                Integer.class, quoteId);
    }

    @Test
    @DisplayName("발송 한 번에 링크·단계·상태가 함께 바뀐다 — 리드 딜은 견적 단계로 올라간다")
    void 발송은_네_가지를_함께_한다() {
        var result = quoteService.send(ctx, quoteId, new QuoteRequests.SendQuote(contactId, "확인 부탁드립니다"));

        assertThat(result.status()).isEqualTo("SENT");
        assertThat(result.dealStage()).isEqualTo("QUOTE");        // 자동 승급이 응답에 반영됐다 (Q-25)
        assertThat(result.version()).isEqualTo(quoteVersion());   // 08 검증 노트 #4

        assertThat(quoteStatus()).isEqualTo("SENT");
        assertThat(dealStage()).isEqualTo("QUOTE");
        assertThat(activeLinkCount()).isOne();                    // 링크가 실제로 남았다 (전이표 §6 효과)
    }

    /**
     * <b>이 테스트가 이 이슈의 핵심이다.</b> 링크 발급이 <b>끝난 뒤</b> 단계 승급에서
     * 실패시키고, 네 가지가 <b>전부</b> 되돌아가는지 본다.
     *
     * <p>하나라도 남으면 표에 없는 상태다 — 링크만 남으면 "SENT가 아닌데 살아 있는 링크"가,
     * 단계만 오르면 "발송하지 않았는데 견적 단계인 딜"이 된다.
     *
     * <p><b>이 자리가 판별력이 있다.</b> {@code send}에서 {@code @Transactional}을 떼면
     * {@code issue()}가 자기 트랜잭션으로 이미 커밋해버려 <b>링크가 남는다</b> — 그때 빨간불이 된다.
     */
    @Test
    @DisplayName("링크 발급 뒤 단계 승급이 실패하면 링크·단계·상태가 전부 되돌아간다 (Q-40)")
    void 링크_발급_뒤_실패는_전부_되돌린다() {
        // 스터빙을 트랜잭션 안에서 한다 — promoteToQuoteStage가 MANDATORY라(#227) 스터빙 호출도
        // Spring 트랜잭션 프록시를 먼저 지나고, 트랜잭션이 없으면 거기서 거부된다.
        // 스터빙이 중간에 끊기면 Mockito 상태가 깨져 다음 테스트 클래스까지 오염된다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                willThrow(new IllegalStateException("승급 실패"))
                        .given(dealCommand).promoteToQuoteStage(any()));

        assertThatThrownBy(() -> quoteService.send(ctx, quoteId, new QuoteRequests.SendQuote(contactId, null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(quoteStatus()).isEqualTo("DRAFT");   // 발송되지 않았다
        assertThat(dealStage()).isEqualTo("LEAD");      // 단계도 그대로다
        assertThat(activeLinkCount()).isZero();         // 링크도 남지 않았다 — 이게 핵심이다
    }

    /**
     * <b>판별력이 없다 — 불변식을 고정하는 자리다.</b> 이 테스트는 {@code flush()} 없이도 통과한다.
     *
     * <p>#182는 "딜이 이미 견적 단계면 승급이 무동작이라 auto-flush가 걸리지 않고, 응답에는 0이
     * DB에는 1이 남는다"를 재현 경로로 제시했다. <b>실제로는 재현되지 않는다</b> — 견적 단계에서도
     * 마지막 Deal 조회가 quote 갱신까지 함께 flush시켜 응답이 1이다. 그 조회를 제거하면 0으로
     * 떨어지는 것까지 실험으로 확인했다. 이 클래스는 롤백 경계를 보려고 {@code @Transactional}을
     * 일부러 빼서 <b>커밋 후만 관찰</b>하므로, 애초에 응답과 DB가 갈릴 수 없는 자리이기도 하다
     * (E 리뷰, #278).
     *
     * <p>그럼에도 남기는 이유는 <b>08 검증 노트 #4("Response는 항상 최신 version")를 규약으로
     * 못박기 위해서</b>다. 지금 값이 맞는 것은 Hibernate가 쿼리 대상 테이블을 계산하는 방식에
     * 딸린 우연이고, Deal 조회가 빠지거나 순서가 바뀌면 조용히 어긋난다.
     *
     * <p>딜이 이미 견적 단계인 경로는 실제로 흔하다 — 딜 하나에 견적을 여러 건 만들 수 있어(QT-18)
     * 두 번째 견적을 발송할 때가 그렇다.
     */
    @Test
    @DisplayName("이미 견적 단계인 딜에 발송해도 응답 version이 DB와 같다 (08 검증 노트 #4)")
    void 승급이_무동작이어도_최신_version이_실린다() {
        jdbc.update("update deal set stage = 'QUOTE' where id = ?", dealId);

        var result = quoteService.send(ctx, quoteId, new QuoteRequests.SendQuote(contactId, null));

        assertThat(result.dealStage()).isEqualTo("QUOTE");   // 무동작이지만 단계는 견적 그대로다
        assertThat(quoteVersion()).isEqualTo(1);             // markSent가 DB에 반영됐다
        assertThat(result.version()).isEqualTo(quoteVersion());
    }

    @Test
    @DisplayName("다른 고객사 담당자를 수신인으로 지정하면 링크를 발급하기 전에 막힌다")
    void 남의_고객사_담당자는_링크_발급_전에_막힌다() {
        UUID 남의_고객사 = UUID.randomUUID();
        UUID 남의_담당자 = UUID.randomUUID();
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                남의_고객사, companyId, memberId, "성원산업");
        jdbc.update("insert into customer_contact (id, customer_id, name, email) values (?, ?, ?, ?)",
                남의_담당자, 남의_고객사, "김민철", "minchul-" + 남의_담당자 + "@sungwon.test");

        try {
            assertThatThrownBy(() -> quoteService.send(ctx, quoteId, new QuoteRequests.SendQuote(남의_담당자, null)))
                    .hasMessageContaining("담당자만");

            assertThat(activeLinkCount()).isZero();     // 링크가 나가지 않았다 — 이게 이 검증의 전부다
            assertThat(quoteStatus()).isEqualTo("DRAFT");
        } finally {
            jdbc.update("delete from customer_contact where id = ?", 남의_담당자);
            jdbc.update("delete from customer where id = ?", 남의_고객사);
        }
    }
}
