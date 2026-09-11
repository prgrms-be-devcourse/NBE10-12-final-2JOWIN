package com.twojo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.order.dto.OrderRequests;
import com.twojo.order.dto.OrderResponses;
import com.twojo.order.service.OrderService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 주문 목록·상세 — <b>실제 쿼리가 도는지</b>를 본다 (OD-08·09, SC-04).
 *
 * <p>{@code OrderServiceTest}는 {@code orderRepository}가 목이라 <b>{@code OrderSpecs}를 한 줄도
 * 실행하지 않는다</b>. 필드명이 하나 틀려도(`createdAt`·`quoteId`·`companyId`) Specification은
 * 호출 시점에 조립되므로 부트스트랩 검증에도 걸리지 않고, {@code GET /orders} 첫 호출에서야
 * 500으로 드러난다. 그 구멍을 메우는 것이 이 테스트다.
 *
 * <p>같은 이유로 <b>{@code QuoteQuery.quoteIdsByDeals}도 여기서 처음 실행된다</b> —
 * 목 기반 테스트는 담당 딜이 빈 경우만 덮어서 쿼리가 short-circuit된다.
 * 주문의 범위는 {@code orders}에 담당자 컬럼도 {@code deal_id}도 없어 <b>quote를 거쳐</b>
 * Deal의 담당자에서 파생하는데(09 §80), 그 두 걸음이 실제로 이어지는지는 DB에서만 보인다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 전환이 커밋되지 않아 목록 조회가
 * 자기 트랜잭션에서만 보이는 데이터를 읽게 된다 ({@code OrderConversionConcurrencyTest}와 같은 이유).
 * 그래서 뒷정리도 {@code @AfterEach}에서 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderListIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final PageRequest 첫_페이지 =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    @Autowired
    private OrderService orderService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID 박지훈;
    private UUID 다른영업;
    private UUID customerId;
    private UUID 내_딜;
    private UUID 남의_딜;
    private UUID 내_견적;
    private UUID 남의_견적;

    private AccessContext 관리자;
    private AccessContext 영업;

    private UUID 내_주문;

    @BeforeEach
    void 승인_견적_두_건을_서로_다른_담당자로_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        박지훈 = UUID.randomUUID();
        다른영업 = UUID.randomUUID();
        customerId = UUID.randomUUID();
        내_딜 = UUID.randomUUID();
        남의_딜 = UUID.randomUUID();
        내_견적 = UUID.randomUUID();
        남의_견적 = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')",
                companyId, applicationId, "한빛오피스", businessNo);
        구성원(박지훈, "박지훈");
        구성원(다른영업, "이서준");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, 박지훈, "도담산업");
        딜(내_딜, 박지훈, "도담 사무가구");
        딜(남의_딜, 다른영업, "도담 창고 선반");
        승인된_견적(내_견적, 내_딜, "Q-9001");
        승인된_견적(남의_견적, 남의_딜, "Q-9002");

        관리자 = new AccessContext(companyId, 박지훈, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
        영업 = new AccessContext(companyId, 박지훈, Role.SALES_REP, AccessScope.OWNED_ONLY);

        // 목록에 실을 데이터는 전환으로 만든다 — 시드 INSERT로 만들면 전환 경로와 갈릴 수 있다
        내_주문 = orderService.convert(관리자, 내_견적).id();
        orderService.convert(관리자, 남의_견적);
    }

    private void 구성원(UUID id, String name) {
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')",
                id, companyId, "sales-" + id + "@twojo.test", name);
    }

    private void 딜(UUID id, UUID assignee, String title) {
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'NEGOTIATION', 0)",
                id, companyId, customerId, assignee, title);
    }

    private void 승인된_견적(UUID id, UUID dealId, String quoteNo) {
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, 'APPROVED', 'EXCLUDED', 1000000, 100000, 1100000, ?, 0)",
                id, companyId, dealId, quoteNo, LocalDate.now(SEOUL).plusDays(30));
        jdbc.update("insert into quote_item (id, quote_id, name, unit, quantity, unit_price, amount, sort_order) "
                        + "values (?, ?, '사무용 의자', 'EA', 10, 100000, 1000000, 0)",
                UUID.randomUUID(), id);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from order_item where order_id in (select id from orders where company_id = ?)", companyId);
        jdbc.update("delete from orders where company_id = ?", companyId);
        jdbc.update("delete from document_sequence where company_id = ?", companyId);
        jdbc.update("delete from quote_item where quote_id in (?, ?)", 내_견적, 남의_견적);
        jdbc.update("delete from quote where id in (?, ?)", 내_견적, 남의_견적);
        jdbc.update("delete from deal where id in (?, ?)", 내_딜, 남의_딜);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id in (?, ?)", 박지훈, 다른영업);
        // 감사 로그는 리스너가 만든 행이다 — 회사보다 먼저 지운다 (#287)
        jdbc.update("delete from audit_log where company_id = ?", companyId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /**
     * <b>이 테스트가 실패하면 {@code GET /orders}가 500이다.</b> {@code OrderSpecs}의 필드명이
     * 실제 매핑과 맞는지를 확인하는 유일한 자리다 — 목은 이걸 삼킨다.
     */
    @Test
    @DisplayName("기업 관리자는 회사 전체 주문을 본다 (SC-05)")
    void 관리자_목록() {
        var page = orderService.list(관리자, null, null, 첫_페이지);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(OrderResponses.OrderRow::quoteNo)
                .containsExactlyInAnyOrder("Q-9001", "Q-9002");
        assertThat(page.content()).allSatisfy(row -> {
            assertThat(row.orderNo()).startsWith("O-");
            assertThat(row.customerName()).isEqualTo("도담산업");   // quote → deal → customer 세 걸음
            assertThat(row.totalAmount()).isEqualTo(1_100_000L);
            assertThat(row.dealId()).isNotNull();                   // orders에 deal_id가 없다
        });
    }

    /**
     * <b>{@code QuoteQuery.quoteIdsByDeals}가 실제로 실행되는 유일한 테스트다.</b>
     * 주문에는 담당자 컬럼이 없어 범위가 <b>quote를 거쳐</b> Deal의 담당자에서 파생한다 (09 §80).
     * 이 두 걸음이 끊기면 영업이 회사 전체 주문을 보게 된다 — SC-04가 통째로 뚫린다.
     */
    @Test
    @DisplayName("영업은 담당 Deal의 주문만 본다 — 남의 딜 주문은 목록에도 상세에도 없다 (SC-04)")
    void 영업_범위() {
        var page = orderService.list(영업, null, null, 첫_페이지);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().quoteNo()).isEqualTo("Q-9001");

        // 목록에서 가려지는 것과 단건 접근이 막히는 것은 다른 경로다 — 둘 다 확인한다
        UUID 남의_주문 = jdbc.queryForObject("select id from orders where quote_id = ?", UUID.class, 남의_견적);
        assertThatThrownBy(() -> orderService.get(영업, 남의_주문))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);   // 존재와 권한을 구별하지 않는다 (SC-09)
    }

    /**
     * 기간은 KST 날짜로 끊고 {@code to}는 그날을 포함한다. {@code OrderServiceTest}가 서비스의
     * 경계 계산을 고정한다면, 여기서는 <b>그 값이 쿼리에서 실제로 걸리는지</b>를 본다 —
     * {@code OrderSpecs}가 부등호를 뒤집어도 목 테스트는 통과한다.
     */
    @Test
    @DisplayName("기간 필터가 쿼리에 실제로 걸린다 — 오늘 전환한 주문은 어제까지로 조회하면 없다 (OD-08)")
    void 기간_필터() {
        LocalDate 오늘 = LocalDate.now(SEOUL);

        assertThat(orderService.list(관리자, 오늘, 오늘, 첫_페이지).totalElements())
                .isEqualTo(2);                                   // to가 오늘을 포함한다
        assertThat(orderService.list(관리자, null, 오늘.minusDays(1), 첫_페이지).totalElements())
                .isZero();                                       // 상한 밖
        assertThat(orderService.list(관리자, 오늘.plusDays(1), null, 첫_페이지).totalElements())
                .isZero();                                       // 하한 밖
    }

    @Test
    @DisplayName("상세에 스냅샷 항목과 quote 경유 정보가 실린다 (OD-09)")
    void 상세() {
        OrderResponses.OrderDetail detail = orderService.get(영업, 내_주문);

        assertThat(detail.quoteNo()).isEqualTo("Q-9001");
        assertThat(detail.dealId()).isEqualTo(내_딜);
        assertThat(detail.dealTitle()).isEqualTo("도담 사무가구");
        assertThat(detail.dealStage()).isEqualTo("WON");          // 전환이 성사시켰다 (OD-06)
        assertThat(detail.customerId()).isEqualTo(customerId);
        assertThat(detail.customerName()).isEqualTo("도담산업");
        assertThat(detail.items()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("사무용 의자");
            assertThat(line.quantity()).isEqualTo(10);
            assertThat(line.amount()).isEqualTo(1_000_000L);
        });
    }

    /**
     * 착수일·납기를 적었다가 지우는 왕복 — <b>지우기가 되는지</b>가 핵심이다.
     * 주문에는 다른 수정 경로가 없어(취소도 없다, Q-09) 여기서 못 지우면 잘못 적은 날짜가 영구히 남는다.
     */
    @Test
    @DisplayName("착수일·납기를 기록했다가 지울 수 있다 — null은 미변경이 아니라 지움 (OD-10)")
    void 일정_기록() {
        LocalDate 착수 = LocalDate.of(2026, 9, 15);
        LocalDate 납기 = LocalDate.of(2026, 10, 15);

        orderService.updateSchedule(영업, 내_주문, new OrderRequests.UpdateSchedule(착수, 납기));
        assertThat(orderService.get(영업, 내_주문).startDate()).isEqualTo(착수);
        assertThat(orderService.get(영업, 내_주문).deliveryDate()).isEqualTo(납기);

        orderService.updateSchedule(영업, 내_주문, new OrderRequests.UpdateSchedule(null, null));
        assertThat(orderService.get(영업, 내_주문).startDate()).isNull();
        assertThat(orderService.get(영업, 내_주문).deliveryDate()).isNull();
    }
}
