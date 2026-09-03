package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.deal.dto.DealRequests;
import java.util.UUID;
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
 * Deal 단계 전이의 낙관적 락 — <b>C의 대표 Evidence</b> (11-work-breakdown.md §4, #12에서 선작성).
 *
 * <p><b>목으로는 성립하지 않는다.</b> 엔티티의 {@code checkVersion}은 "요청이 들고 온 version이
 * 지금 값과 다른가"만 본다 — 낡은 화면을 잡는 검사다. 그런데 <b>둘이 같은 version을 읽고
 * 동시에 커밋</b>하면 양쪽 다 그 검사를 통과하고, 진 쪽은 flush 시점에 JPA {@code @Version}에
 * 걸린다. 그 경로는 실제 DB의 UPDATE ... WHERE version = ? 가 있어야 재현된다.
 *
 * <p>이 테스트가 지키는 것은 <b>응답 코드의 일관성</b>이다. 두 경로 모두 "version 불일치"인데,
 * {@code GlobalExceptionHandler}에 {@code ObjectOptimisticLockingFailureException} 매핑이 없으면
 * 동시 쓰기만 500으로 나간다. 그 매핑을 지우면 이 테스트만 빨간불이 된다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 두 호출이 테스트 트랜잭션에 합류해
 * 커밋이 미뤄지고 경쟁 자체가 사라진다. D의 {@code ViewTokenCommandIssueIntegrationTest}와 같은 이유다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DealStageConcurrencyTest {

    @Autowired
    private DealService dealService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID dealId;
    private AccessContext ctx;

    @BeforeEach
    void 딜_체인을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        dealId = UUID.randomUUID();
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
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'LEAD', 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    @Test
    @DisplayName("같은 version으로 동시에 단계를 올리면 한쪽만 성공한다 — 단계는 한 칸만 움직인다")
    void 동시_단계_전이는_한쪽만_성공한다() throws InterruptedException {
        int threads = 8;
        CountDownLatch 출발선 = new CountDownLatch(1);
        CountDownLatch 종료 = new CountDownLatch(threads);
        AtomicInteger 성공 = new AtomicInteger();
        AtomicInteger 실패 = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        출발선.await();   // 순차 실행이 되면 검증이 무의미하다
                        dealService.advance(ctx, dealId, new DealRequests.StageMove(0));
                        성공.incrementAndGet();
                    } catch (Exception e) {
                        실패.incrementAndGet();
                    } finally {
                        종료.countDown();
                    }
                });
            }
            출발선.countDown();
            assertThat(종료.await(20, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(성공.get()).isOne();
        assertThat(실패.get()).isEqualTo(threads - 1);

        // 단계가 여러 칸 뛰지 않았는지 — 표에 없는 전이가 동시성으로 만들어지면 안 된다 (전이표 §5)
        assertThat(jdbc.queryForObject("select stage from deal where id = ?", String.class, dealId))
                .isEqualTo("CONSULT");
        assertThat(jdbc.queryForObject("select version from deal where id = ?", Integer.class, dealId))
                .isOne();
    }
}
