package com.twojo.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.quote.dto.QuoteResponses;
import com.twojo.quote.service.QuoteService;
import java.time.LocalDate;
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

/**
 * 견적 복제 (QT-19) — <b>실제 DB로 돌린다</b>.
 *
 * <p>단위 테스트가 못 잡는 것을 본다. {@code cloneAsDraft}는 새 {@code QuoteItem} 인스턴스를
 * {@code replaceItems}로 넣는데, <b>그것이 실제로 persist되는지는 JPA cascade에 달려 있다</b> —
 * 목에서는 리스트에 담기기만 해도 통과하지만, cascade가 없으면 DB에는 항목 없는 견적이 남는다.
 * 반려 견적을 복제해 다시 보내는 것이 이 기능의 주 용도인데(Q-18) 항목이 빠지면 빈 견적이 나간다.
 *
 * <p>채번({@code document_sequence})과 {@code cloned_from_quote_id} FK도 실제로 걸려야 한다 —
 * 둘 다 트랜잭션·제약 조건이 관여해 단위로는 증명되지 않는다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 커밋된 뒤에 다시 읽어야 저장을 확인할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QuoteCloneIntegrationTest {

    @Autowired
    private QuoteService quoteService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID dealId;
    private UUID quoteId;
    private AccessContext ctx;

    @BeforeEach
    void 반려된_견적을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
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
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'NEGOTIATION', 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");
        // 반려된 원본 — 발송·응답 이력이 다 들어 있다
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, terms, "
                        + "supply_amount, vat_amount, total_amount, valid_until, sent_at, responded_at, "
                        + "reject_reason, responder_name, version) "
                        + "values (?, ?, ?, 'Q-9501', 'REJECTED', 'INCLUDED', '결제는 납품 후 30일', "
                        + "800000, 80000, 880000, ?, now(), now(), '예산 초과', '이수정', 0)",
                quoteId, companyId, dealId, LocalDate.now().minusDays(5));
        항목("책상", 3, 200_000L, 600_000L, 0, null);
        항목("의자", 5, 40_000L, 200_000L, 1, 45_000L);

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    private void 항목(String name, int qty, long unitPrice, long amount, int sortOrder, Long catalogPrice) {
        jdbc.update("insert into quote_item (id, quote_id, name, unit, quantity, unit_price, amount, "
                        + "catalog_price_at_creation, sort_order) values (?, ?, ?, 'EA', ?, ?, ?, ?, ?)",
                UUID.randomUUID(), quoteId, name, qty, unitPrice, amount, catalogPrice, sortOrder);
    }

    private List<Map<String, Object>> 항목들(UUID id) {
        return jdbc.queryForList(
                "select name, quantity, unit_price, amount, catalog_price_at_creation, sort_order "
                        + "from quote_item where quote_id = ? order by sort_order", id);
    }

    /**
     * <b>이 테스트가 이 PR의 핵심이다.</b> cascade가 빠지면 여기서만 드러난다 —
     * 단위 테스트는 리스트에 담긴 것만 보고 통과한다.
     */
    @Test
    @DisplayName("항목이 실제로 저장된다 — cascade가 없으면 빈 견적이 남는다")
    void 항목이_DB에_저장된다() {
        QuoteResponses.QuoteDetail copy = quoteService.clone(ctx, quoteId);

        List<Map<String, Object>> saved = 항목들(copy.id());
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(r -> r.get("name")).containsExactly("책상", "의자");
        assertThat(saved).extracting(r -> ((Number) r.get("sort_order")).intValue()).containsExactly(0, 1);
        assertThat(saved).extracting(r -> ((Number) r.get("amount")).longValue())
                .containsExactly(600_000L, 200_000L);
        // 카탈로그 단가 스냅샷(QT-24)도 함께 넘어간다 — 직접 입력 줄은 null 그대로다
        assertThat(saved.get(0).get("catalog_price_at_creation")).isNull();
        assertThat(((Number) saved.get(1).get("catalog_price_at_creation")).longValue()).isEqualTo(45_000L);
    }

    @Test
    @DisplayName("금액이 항목에서 재계산되어 저장된다 — 원본 합계와 같다")
    void 금액이_재계산된다() {
        QuoteResponses.QuoteDetail copy = quoteService.clone(ctx, quoteId);

        Map<String, Object> row = jdbc.queryForMap(
                "select supply_amount, vat_amount, total_amount, status, vat_mode, terms, cloned_from_quote_id "
                        + "from quote where id = ?", copy.id());

        assertThat(((Number) row.get("supply_amount")).longValue()).isEqualTo(800_000L);
        assertThat(((Number) row.get("total_amount")).longValue()).isEqualTo(880_000L);
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("vat_mode")).isEqualTo("INCLUDED");
        assertThat(row.get("terms")).isEqualTo("결제는 납품 후 30일");
        assertThat(row.get("cloned_from_quote_id")).isEqualTo(quoteId);
    }

    @Test
    @DisplayName("원본은 항목까지 그대로다 — 복제는 읽기다 (전이표 §6)")
    void 원본은_그대로다() {
        quoteService.clone(ctx, quoteId);

        assertThat(항목들(quoteId)).hasSize(2);
        Map<String, Object> origin = jdbc.queryForMap(
                "select status, quote_no, sent_at, reject_reason from quote where id = ?", quoteId);
        assertThat(origin.get("status")).isEqualTo("REJECTED");
        assertThat(origin.get("quote_no")).isEqualTo("Q-9501");
        assertThat(origin.get("sent_at")).isNotNull();
        assertThat(origin.get("reject_reason")).isEqualTo("예산 초과");
    }

    /**
     * 채번은 {@code document_sequence} 행에 배타 락을 걸어 올린다 — 트랜잭션이 관여해
     * 단위로는 증명되지 않는다. 복제가 그 경로를 실제로 지나는지 본다.
     */
    @Test
    @DisplayName("새 번호가 채번된다 — 두 번 복제하면 번호가 연속이다")
    void 채번이_실제로_돈다() {
        String first = quoteService.clone(ctx, quoteId).quoteNo();
        String second = quoteService.clone(ctx, quoteId).quoteNo();

        assertThat(first).isNotEqualTo("Q-9501").isNotEqualTo(second);
        assertThat(Integer.parseInt(second.substring(second.lastIndexOf('-') + 1)))
                .isEqualTo(Integer.parseInt(first.substring(first.lastIndexOf('-') + 1)) + 1);
    }

    @Test
    @DisplayName("이력은 비어서 저장된다 — 새 견적이 보낸 적 있는 상태로 태어나지 않는다")
    void 이력은_비어_있다() {
        QuoteResponses.QuoteDetail copy = quoteService.clone(ctx, quoteId);

        Map<String, Object> row = jdbc.queryForMap(
                "select sent_at, first_viewed_at, responded_at, reject_reason, responder_name "
                        + "from quote where id = ?", copy.id());
        assertThat(row.values()).containsOnlyNulls();
    }

    @Test
    @DisplayName("유효기간은 지난 원본에서도 미래로 잡힌다 — 만들자마자 만료되지 않는다 (QT-31)")
    void 유효기간이_미래다() {
        QuoteResponses.QuoteDetail copy = quoteService.clone(ctx, quoteId);

        LocalDate saved = jdbc.queryForObject(
                "select valid_until from quote where id = ?", LocalDate.class, copy.id());
        assertThat(saved).isAfter(LocalDate.now());
    }

    /**
     * 종결 Deal에서 막힐 때 <b>채번이 소비되지 않아야</b> 한다 — 실패한 요청마다 번호에
     * 구멍이 생기면 견적번호가 띄엄띄엄해진다.
     */
    @Test
    @DisplayName("종결 Deal이면 409이고 채번도 견적도 남지 않는다 (Q-25)")
    void 종결_Deal은_아무것도_남기지_않는다() {
        jdbc.update("update deal set stage = 'LOST', lost_from_stage = 'NEGOTIATION', "
                + "lost_reason = '예산 미확보' where id = ?", dealId);
        long before = 견적수();

        assertThatThrownBy(() -> quoteService.clone(ctx, quoteId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.QUOTE_DEAL_CLOSED);

        assertThat(견적수()).isEqualTo(before);
        assertThat(jdbc.queryForList("select 1 from document_sequence where company_id = ?", companyId))
                .isEmpty();
    }

    private long 견적수() {
        return jdbc.queryForObject("select count(*) from quote where company_id = ?", Long.class, companyId);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from quote_item where quote_id in (select id from quote where company_id = ?)",
                companyId);
        jdbc.update("delete from quote where company_id = ?", companyId);
        jdbc.update("delete from document_sequence where company_id = ?", companyId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }
}
