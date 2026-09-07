package com.twojo.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.quote.dto.QuoteRequests;
import com.twojo.quote.service.QuoteService;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 견적 수정의 낙관적 락 — <b>C의 대표 Evidence</b> (11-work-breakdown.md §4).
 *
 * <p>{@code quote}·{@code deal}만 {@code @Version}을 가진다 (업무 분담 §1.1).
 * Deal 전이 쪽은 {@code DealStageConcurrencyTest}가 맡고(#61), 여기는 견적 수정이다.
 *
 * <p><b>목으로는 성립하지 않는다.</b> 진 쪽에 도달하는 예외가 <b>두 갈래</b>인데, 어느 쪽이
 * 나올지는 실제 커밋 타이밍이 정한다.
 * <ul>
 *   <li>{@link BusinessException}({@code STALE_VERSION}) — 승자가 커밋한 <b>뒤에</b> 읽은 쪽.
 *       엔티티의 {@code checkVersion}이 "요청이 들고 온 0"과 "지금 값 1"의 불일치를 잡는다</li>
 *   <li>{@link ObjectOptimisticLockingFailureException} — 승자와 <b>같은 version 0을 읽고</b>
 *       동시에 커밋한 쪽. checkVersion을 통과한 뒤 flush 시점에 JPA {@code @Version}이 잡는다</li>
 * </ul>
 * 둘 다 409 STALE_VERSION으로 나가야 한다 — 사용자가 할 일이 "새로고침 후 재시도"로 같기 때문이다
 * (07 부록). 두 번째 갈래의 매핑은 {@code GlobalExceptionHandler}에 있고, 그게 없으면
 * {@code Exception} 폴백으로 떨어져 500이 된다 — 같은 "version 불일치"인데 응답이 갈린다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 호출들이 테스트 트랜잭션에 합류해
 * 커밋이 미뤄지고 경쟁 자체가 사라진다. {@code DealStageConcurrencyTest}와 같은 이유다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QuoteOptimisticLockTest {

    private static final int 동시_요청 = 8;

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
    void 견적까지_심는다() {
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
                        + "values (?, ?, ?, ?, ?, 'QUOTE', 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);

        // 견적은 서비스로 만든다 — 채번(MANDATORY)까지 실제 경로를 지나야 version 0이 DB에 박힌다
        quoteId = UUID.fromString(quoteService.create(ctx, new QuoteRequests.CreateQuote(dealId)).id().toString());
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from quote_item where quote_id = ?", quoteId);
        jdbc.update("delete from quote where id = ?", quoteId);
        jdbc.update("delete from document_sequence where company_id = ?", companyId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /** 직접 입력 항목만 쓴다 — 카탈로그를 지나면 이 테스트가 보는 것이 낙관적 락이 아니게 된다 (QT-03) */
    private static QuoteRequests.UpdateQuote 수정요청(int version, long 단가) {
        return new QuoteRequests.UpdateQuote(
                LocalDate.now().plusDays(15), "EXCLUDED", "납기 2주",
                List.of(new QuoteRequests.UpdateQuote.Item(
                        null, "현장 실측 및 배치 설계", "식", 1, 단가, 0)),
                version);
    }

    @Test
    @DisplayName("같은 견적을 동시에 수정하면 한쪽만 성공한다 — 나머지는 전부 409로 갈 예외를 받는다")
    void 동시_수정은_한쪽만_성공한다() throws InterruptedException {
        CountDownLatch 출발선 = new CountDownLatch(1);
        CountDownLatch 종료 = new CountDownLatch(동시_요청);
        AtomicInteger 성공 = new AtomicInteger();
        Queue<Throwable> 실패들 = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(동시_요청)) {
            for (int i = 0; i < 동시_요청; i++) {
                long 단가 = 100_000L * (i + 1);   // 스레드마다 다른 값 — 누가 이겼는지 구분된다
                pool.execute(() -> {
                    try {
                        출발선.await();   // 순차 실행이 되면 검증이 무의미하다
                        quoteService.update(ctx, quoteId, 수정요청(0, 단가));
                        성공.incrementAndGet();
                    } catch (Throwable e) {
                        실패들.add(e);
                    } finally {
                        종료.countDown();
                    }
                });
            }
            출발선.countDown();
            assertThat(종료.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(성공.get()).isOne();
        assertThat(실패들).hasSize(동시_요청 - 1);

        // 진 쪽이 무엇을 받는지 고정한다 — 두 갈래 다 409 STALE_VERSION으로 나가야 한다.
        // 예외를 뭉뚱그려 세면 낙관적 락 매핑이 사라져도 이 테스트는 초록을 유지한다.
        assertThat(실패들).allSatisfy(e -> assertThat(e).satisfiesAnyOf(
                stale -> assertThat(stale)
                        .isInstanceOf(BusinessException.class)
                        .extracting(x -> ((BusinessException) x).getErrorCode())
                        .isEqualTo(ErrorCode.STALE_VERSION),
                concurrent -> assertThat(concurrent)
                        .isInstanceOf(ObjectOptimisticLockingFailureException.class)));

        // 수정이 정확히 한 번만 반영됐는지 — version이 2 이상이면 두 요청이 함께 통과한 것이다
        assertThat(jdbc.queryForObject("select version from quote where id = ?", Integer.class, quoteId))
                .isOne();
        assertThat(jdbc.queryForObject("select count(*) from quote_item where quote_id = ?",
                Integer.class, quoteId)).isOne();
    }
}
