package com.twojo.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.order.dto.OrderResponses;
import com.twojo.order.service.OrderService;
import java.time.LocalDate;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 주문 전환 동시성 검증 — <b>C의 대표 Evidence</b> (11-work-breakdown.md §4).
 *
 * <p>1주차에 선작성하고 주문 전환 이슈(#160)에서 활성화했다.
 *
 * <p>검증 대상은 §4의 주문 전환 트랜잭션이다.
 * <pre>
 * BEGIN
 *   SELECT ... FROM quote WHERE id = ? FOR UPDATE
 *   status = APPROVED 검증                    -- 아니면 QUOTE_NOT_APPROVED (OD-02)
 *   orders INSERT (금액·항목 값 복사)          -- 중복이면 UNIQUE(quote_id) 위반
 *                                             -- → QUOTE_ALREADY_CONVERTED (OD-03)
 *   deal.stage = WON (이미 WON이면 유지 — 멱등) -- OD-06, Q-25
 * COMMIT
 * </pre>
 *
 * <p><b>목으로는 성립하지 않는다.</b> {@code FOR UPDATE}는 실제 DB에서만 줄을 세우고,
 * {@code orders.quote_id UNIQUE}도 마찬가지다. 서비스가 "이미 주문이 있는가"를 락 <b>안에서</b>
 * 보는지 아니면 밖에서 보는지가 여기서만 드러난다 — 밖이면 여럿이 함께 통과해
 * UNIQUE가 터지고, 그건 409가 아니라 <b>500</b>이다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 호출들이 테스트 트랜잭션에 합류해
 * 커밋이 미뤄지고 경쟁 자체가 사라진다 ({@code DealStageConcurrencyTest}와 같은 이유).
 * 그래서 뒷정리도 {@code @AfterEach}에서 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderConversionConcurrencyTest {

    private static final int THREADS = 100;

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
    private UUID secondQuoteId;
    private AccessContext ctx;

    @BeforeEach
    void 승인된_견적을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        secondQuoteId = UUID.randomUUID();
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
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'NEGOTIATION', 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");

        승인된_견적(quoteId, "Q-9001");
        승인된_견적(secondQuoteId, "Q-9002");   // Q-25 — 같은 딜의 두 번째 승인 견적

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    private void 승인된_견적(UUID id, String quoteNo) {
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, 'APPROVED', 'EXCLUDED', 1000000, 100000, 1100000, ?, 0)",
                id, companyId, dealId, quoteNo, LocalDate.now().plusDays(30));
        jdbc.update("insert into quote_item (id, quote_id, name, unit, quantity, unit_price, amount, sort_order) "
                        + "values (?, ?, '사무용 의자', 'EA', 10, 100000, 1000000, 0)",
                UUID.randomUUID(), id);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from order_item where order_id in (select id from orders where company_id = ?)", companyId);
        jdbc.update("delete from orders where company_id = ?", companyId);
        jdbc.update("delete from document_sequence where company_id = ?", companyId);
        jdbc.update("delete from quote_item where quote_id in (?, ?)", quoteId, secondQuoteId);
        jdbc.update("delete from quote where id in (?, ?)", quoteId, secondQuoteId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    @Test
    @DisplayName("동일 견적 100건 동시 전환 → 주문 1건만 생성된다 (OD-03)")
    void 동시_전환은_1건만_성공한다() throws InterruptedException {
        CountDownLatch 출발선 = new CountDownLatch(1);
        CountDownLatch 종료 = new CountDownLatch(THREADS);
        AtomicInteger 성공 = new AtomicInteger();
        Queue<Throwable> 실패들 = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                pool.execute(() -> {
                    try {
                        출발선.await();   // 순차 실행이 되면 검증이 무의미하다
                        orderService.convert(ctx, quoteId);
                        성공.incrementAndGet();
                    } catch (Throwable e) {
                        실패들.add(e);
                    } finally {
                        종료.countDown();
                    }
                });
            }
            출발선.countDown();
            assertThat(종료.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(성공.get()).isOne();
        assertThat(실패들).hasSize(THREADS - 1);

        // 진 쪽이 무엇을 받는지 고정한다. UNIQUE(quote_id)가 최종 방어선이라 주문 수는 어차피 1이지만,
        // 서비스가 락 안에서 먼저 잡지 못하면 여기서 409가 아니라 DataIntegrityViolation(→ 500)이 섞인다
        assertThat(실패들).allSatisfy(e -> assertThat(e)
                .isInstanceOf(BusinessException.class)
                .extracting(x -> ((BusinessException) x).getErrorCode())
                .isEqualTo(ErrorCode.QUOTE_ALREADY_CONVERTED));

        assertThat(jdbc.queryForObject("select count(*) from orders where quote_id = ?", Integer.class, quoteId))
                .isOne();
        // 채번도 한 번만 소비돼야 한다 — 실패한 99건이 번호를 먼저 뽑았다면 카운터가 100까지 올라간다
        assertThat(jdbc.queryForObject(
                "select last_seq from document_sequence where company_id = ? and doc_type = 'ORDER'",
                Integer.class, companyId)).isOne();
        assertThat(jdbc.queryForObject("select stage from deal where id = ?", String.class, dealId))
                .isEqualTo("WON");
    }

    /**
     * <b>{@code markWon}이 멱등이어야 통과한다</b> (Q-25, 07 §C 257행).
     * 발송({@code promoteToQuoteStage})이 종결 딜에서 던지는 것과 <b>반대</b>라 헷갈리기 쉬운 자리다 —
     * 성사 전에 발송된 견적은 끝까지 유효하므로, 두 번째 승인 견적의 전환은 정상 시나리오다.
     */
    @Test
    @DisplayName("이미 성사된 Deal의 두 번째 승인 견적도 전환된다 — 멱등 (Q-25)")
    void 이미_WON인_딜은_상태를_유지한다() {
        OrderResponses.OrderDetail 첫번째 = orderService.convert(ctx, quoteId);   // 협상 → 성사

        // 응답이 방금 일으킨 자동 성사를 반영하는지. markWon을 응답 조립 뒤로 옮기거나
        // 딜 조회가 별도 트랜잭션으로 갈리면 여기서 전환 전 단계(NEGOTIATION)가 나온다
        assertThat(첫번째.dealStage()).isEqualTo("WON");
        assertThat(jdbc.queryForObject("select stage from deal where id = ?", String.class, dealId))
                .isEqualTo("WON");

        OrderResponses.OrderDetail 두번째 = orderService.convert(ctx, secondQuoteId);

        // DEAL_ALREADY_WON을 던지지 않는다 (검증 노트 #2) — 주문이 하나 더 붙고 딜은 WON 그대로다
        assertThat(두번째.dealStage()).isEqualTo("WON");
        assertThat(jdbc.queryForObject("select count(*) from orders where company_id = ?", Integer.class, companyId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select stage from deal where id = ?", String.class, dealId))
                .isEqualTo("WON");
    }
}
