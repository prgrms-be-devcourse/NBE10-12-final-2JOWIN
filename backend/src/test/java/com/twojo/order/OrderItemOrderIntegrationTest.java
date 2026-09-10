package com.twojo.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.order.dto.OrderResponses;
import com.twojo.order.service.OrderService;
import java.time.LocalDate;
import java.util.List;
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
 * 주문 항목 순서가 <b>재조회에서도 유지되는지</b> (V301, PR #197 리뷰 요청).
 *
 * <p><b>전환 직후 응답만 보면 이 문제가 안 잡힌다.</b> 그때는 방금 만든 엔티티가 1차 캐시에 그대로
 * 있어 <b>삽입 순서</b>가 나오고, 정렬이 없어도 통과한다. 실제로 갈리는 것은 캐시가 빈 뒤 DB에서
 * 다시 읽을 때다 — 그래서 {@link EntityManager#clear()}로 비우고 다시 조회한다.
 *
 * <p><b>견적 항목의 {@code sortOrder}를 목록 순서와 어긋나게 심는다.</b> 0·1·2 순서로만 넣으면
 * 삽입 순서와 구별되지 않아 정렬이 빠져도 초록이 된다 — 이 테스트가 아무것도 증명하지 못한다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 전환이 커밋돼야 재조회가 의미를 갖는다.
 * 뒷정리는 {@code @AfterEach}에서 직접 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderItemOrderIntegrationTest {

    /** 견적서에 보이는 순서 — 이 순서가 주문서에서도 그대로 나와야 한다 (QT-07 → OD-04) */
    private static final List<String> 견적_순서 = List.of("첫째 품목", "둘째 품목", "셋째 품목");

    @Autowired
    private OrderService orderService;
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
    void 순서가_뒤섞인_승인_견적을_심는다() {
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
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, 'Q-9101', 'APPROVED', 'EXCLUDED', 300000, 30000, 330000, ?, 0)",
                quoteId, companyId, dealId, LocalDate.now().plusDays(30));

        // 삽입 순서와 sortOrder를 어긋나게 심는다 — 셋째 · 첫째 · 둘째 순으로 넣고 sortOrder는 2 · 0 · 1
        항목("셋째 품목", 2);
        항목("첫째 품목", 0);
        항목("둘째 품목", 1);

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    private void 항목(String name, int sortOrder) {
        jdbc.update("insert into quote_item (id, quote_id, name, unit, quantity, unit_price, amount, sort_order) "
                        + "values (?, ?, ?, 'EA', 1, 100000, 100000, ?)",
                UUID.randomUUID(), quoteId, name, sortOrder);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from order_item where order_id in (select id from orders where company_id = ?)", companyId);
        jdbc.update("delete from orders where company_id = ?", companyId);
        jdbc.update("delete from document_sequence where company_id = ?", companyId);
        jdbc.update("delete from quote_item where quote_id = ?", quoteId);
        jdbc.update("delete from quote where id = ?", quoteId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        // 감사 로그는 리스너가 만든 행이다 — 회사보다 먼저 지운다 (#287)
        jdbc.update("delete from audit_log where company_id = ?", companyId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /**
     * <b>이 테스트가 없으면 정렬이 빠져도 아무도 모른다.</b> 전환 응답은 1차 캐시의 삽입 순서라
     * 항상 맞고, 재조회에서만 DB 순서가 드러난다.
     *
     * <p><b>물리적 순서를 일부러 어긋나게 만든다.</b> 항목은 견적의 {@code @OrderBy} 덕분에
     * 언제나 {@code sortOrder} 순으로 삽입되므로, 그냥 다시 읽으면 힙 순서가 우연히 정답과 같아
     * <b>정렬이 빠져도 통과한다</b>(실제로 확인했다). PostgreSQL은 UPDATE 시 새 튜플을 뒤에 쌓으므로,
     * 첫 항목을 한 번 건드리면 힙 순서가 1·2·0이 된다 — 그때 비로소 {@code ORDER BY}의 유무가 갈린다.
     */
    @Test
    @DisplayName("전환 응답과 재조회가 같은 순서다 — 물리적 순서가 어긋나도 견적 순서가 유지된다")
    void 재조회_순서_유지() {
        OrderResponses.OrderDetail 전환직후 = orderService.convert(ctx, quoteId);

        assertThat(전환직후.items()).extracting(OrderResponses.OrderDetail.Line::name)
                .containsExactlyElementsOf(견적_순서);

        // 첫 항목을 갱신해 힙 뒤로 보낸다 — 물리적 순서가 sortOrder와 어긋나는 상태를 만든다.
        // 판별력은 여기서 나온다: @OrderBy가 없으면 이 교란 뒤의 조회가 삽입 순서를 잃는다.
        // 별도 트랜잭션의 UPDATE라 아래 조회는 새 영속성 컨텍스트에서 시작한다.
        jdbc.update("update order_item set amount = amount where order_id = ? and sort_order = 0",
                전환직후.id());

        OrderResponses.OrderDetail 재조회 = orderService.get(ctx, 전환직후.id());

        assertThat(재조회.items()).extracting(OrderResponses.OrderDetail.Line::name)
                .containsExactlyElementsOf(견적_순서);
    }

    /**
     * 순서는 <b>값으로 복사</b>된다 (OD-04) — `order_item`에 견적 FK가 없어 조인으로 알아낼 수 없다.
     * DB에 실제로 어떤 값이 들어갔는지 직접 본다.
     */
    @Test
    @DisplayName("order_item.sort_order에 견적 항목의 값이 그대로 들어간다 (OD-04)")
    void 순서_값_복사() {
        UUID orderId = orderService.convert(ctx, quoteId).id();

        List<String> 이름들 = jdbc.queryForList(
                "select name from order_item where order_id = ? order by sort_order", String.class, orderId);

        assertThat(이름들).containsExactlyElementsOf(견적_순서);
    }
}
