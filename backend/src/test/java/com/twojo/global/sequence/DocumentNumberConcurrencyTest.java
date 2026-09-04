package com.twojo.global.sequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.global.sequence.DocumentSequence.DocType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 동시 채번 — <b>번호가 겹치지 않는지는 실제 DB에서만 검증된다</b>.
 *
 * <p>{@code SELECT ... FOR UPDATE}가 실제로 행 락을 거는지, 그 락이 트랜잭션 끝까지 유지되는지는
 * 목으로 재현할 수 없다. 락이 걸리지 않으면 여러 요청이 같은 {@code last_seq}를 읽고
 * <b>같은 번호</b>를 받는다 — {@code uk_quote}가 막아주긴 하지만 그건 최종 방어선이지 채번이 아니다.
 *
 * <p>카운터 행이 <b>없는 상태에서</b> 시작한다 — 그 달의 첫 발급이 동시에 들어오는 경로
 * ({@code insertIfAbsent})까지 같은 테스트가 지난다.
 *
 * <p>{@code @Transactional}을 붙이지 않고 {@link TransactionTemplate}으로 스레드마다 트랜잭션을
 * 연다. 테스트 트랜잭션에 합류시키면 커밋이 미뤄져 경쟁 자체가 사라지고,
 * {@code MANDATORY}인 발급 메서드는 트랜잭션 없이는 아예 부를 수 없다.
 * ({@code DealStageConcurrencyTest}와 같은 이유)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DocumentNumberConcurrencyTest {

    private static final int 동시_요청 = 8;

    @Autowired
    private DocumentNumberService documentNumberService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private String yearMonth;

    @BeforeEach
    void 회사를_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);
        // 서비스와 같은 기준으로 계산한다 — 여기서 UTC를 쓰면 월말에만 어긋나는 테스트가 된다
        yearMonth = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyMM"));

        jdbc.update("insert into application (id, company_name, business_no, email, status) "
                        + "values (?, ?, ?, ?, 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')",
                companyId, applicationId, "한빛오피스", businessNo);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from document_sequence where company_id = ?", companyId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    @Test
    @DisplayName("여덟 요청이 동시에 번호를 받아도 겹치지 않는다 — 001~008이 하나씩 나온다")
    void 동시_발급은_번호가_겹치지_않는다() throws InterruptedException {
        CountDownLatch 출발선 = new CountDownLatch(1);
        CountDownLatch 종료 = new CountDownLatch(동시_요청);
        Queue<String> 발급된_번호 = new ConcurrentLinkedQueue<>();
        Queue<Throwable> 실패들 = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(동시_요청)) {
            for (int i = 0; i < 동시_요청; i++) {
                pool.execute(() -> {
                    try {
                        출발선.await();   // 순차 실행이 되면 검증이 무의미하다
                        발급된_번호.add(transactionTemplate.execute(
                                status -> documentNumberService.next(companyId, DocType.QUOTE)));
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

        assertThat(실패들).isEmpty();
        assertThat(발급된_번호).hasSize(동시_요청).doesNotHaveDuplicates();

        // 구멍도 건너뜀도 없이 1부터 이어진다 — 중복만 보면 "전부 실패하고 하나만 성공"도 통과한다
        List<String> 기대 = java.util.stream.IntStream.rangeClosed(1, 동시_요청)
                .mapToObj(seq -> "Q-%s-%03d".formatted(yearMonth, seq))
                .toList();
        assertThat(발급된_번호).containsExactlyInAnyOrderElementsOf(기대);

        assertThat(jdbc.queryForObject(
                "select last_seq from document_sequence where company_id = ? and doc_type = 'QUOTE' and year_month = ?",
                Integer.class, companyId, yearMonth)).isEqualTo(동시_요청);
    }

    @Test
    @DisplayName("트랜잭션 없이 부르면 그 자리에서 막힌다 — 락 없는 채번이 조용히 성공하지 않는다")
    void 트랜잭션_밖_호출은_막힌다() {
        assertThatThrownBy(() -> documentNumberService.next(companyId, DocType.QUOTE))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
