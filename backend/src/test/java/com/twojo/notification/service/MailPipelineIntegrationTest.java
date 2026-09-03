package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MailCommand.TemplateType;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 메일 파이프라인 실 PG 검증 — {@code schedule()} → 커밋 → AFTER_COMMIT 리스너 → {@code @Async} 디스패처 →
 * {@code email_log} SENT/FAILED. 목 단위 테스트가 못 재현하는 것: 실제 트랜잭션 커밋에서만 뜨는 AFTER_COMMIT,
 * 스레드 홉, REQUIRES_NEW 격리, {@code uk_email_log_dedup}.
 *
 * <p><b>클래스에 {@code @Transactional}을 붙이지 않는다.</b> 붙이면 {@code schedule()}이 테스트 트랜잭션에
 * 합류해 커밋이 미뤄지고 AFTER_COMMIT이 영영 안 뜬다 → 테스트가 조용히 무의미해진다. 대신 {@link AfterEach}로
 * 수동 정리한다. {@code EmailSender}는 {@link MockitoBean}으로 세운다(로그 구현은 실패 경로가 없어서).
 *
 * <p>{@code TemplateType}은 {@code SIGNUP_APPROVED}를 쓴다 — 이 타입만 {@code companyId = null}이 계약상
 * 합법이라 {@code company} 시드 없이 자족한다. 검증 대상(AFTER_COMMIT·스레드 홉·REQUIRES_NEW·UNIQUE·MANDATORY)은
 * 타입과 무관하다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailPipelineIntegrationTest {

    @Autowired
    private MailCommand mailCommand;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager txManager;
    @MockitoBean
    private EmailSender emailSender;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from email_log where recipient_email like 'pipeline-%@test'");
    }

    private String statusOf(String recipient) {
        return jdbc.queryForObject(
                "select status from email_log where recipient_email = ? order by created_at desc limit 1",
                String.class, recipient);
    }

    private Integer countOf(String recipient) {
        return jdbc.queryForObject(
                "select count(*) from email_log where recipient_email = ?", Integer.class, recipient);
    }

    @Test
    @DisplayName("예약 후 커밋되면 AFTER_COMMIT 비동기 디스패처가 발송하고 email_log가 SENT가 된다")
    void 예약_커밋_후_SENT() {
        UUID refId = UUID.randomUUID();
        String recipient = "pipeline-ok-" + refId + "@test";

        tx.executeWithoutResult(t ->
                mailCommand.schedule(TemplateType.SIGNUP_APPROVED, null, recipient, refId, "제목", "본문 http://x/q/raw"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(statusOf(recipient)).isEqualTo("SENT"));
        assertThat(jdbc.queryForObject(
                "select sent_at from email_log where recipient_email = ?", Timestamp.class, recipient)).isNotNull();
        verify(emailSender).send(eq(recipient), eq("제목"), eq("본문 http://x/q/raw"));
    }

    @Test
    @DisplayName("호출자가 롤백하면 email_log 행이 생기지 않고 디스패처도 호출되지 않는다")
    void 호출자_롤백이면_행도_디스패처도_없다() {
        UUID refId = UUID.randomUUID();
        String recipient = "pipeline-rollback-" + refId + "@test";

        tx.executeWithoutResult(t -> {
            mailCommand.schedule(TemplateType.SIGNUP_APPROVED, null, recipient, refId, "제목", "본문");
            t.setRollbackOnly();
        });

        assertThat(countOf(recipient)).isZero();
        verify(emailSender, after(500).never()).send(eq(recipient), any(), any());
    }

    @Test
    @DisplayName("트랜잭션 밖에서 schedule()을 부르면 IllegalTransactionStateException으로 거부된다 (MANDATORY)")
    void 트랜잭션_밖_호출은_거부된다() {
        assertThatThrownBy(() -> mailCommand.schedule(
                TemplateType.SIGNUP_APPROVED, null, "pipeline-notx@test", UUID.randomUUID(), "제목", "본문"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("발송기가 예외를 던지면 email_log가 FAILED가 된다 (재시도 없음)")
    void 발송_실패면_FAILED() {
        UUID refId = UUID.randomUUID();
        String recipient = "pipeline-fail-" + refId + "@test";
        willThrow(new RuntimeException("SMTP down")).given(emailSender).send(eq(recipient), any(), any());

        tx.executeWithoutResult(t ->
                mailCommand.schedule(TemplateType.SIGNUP_APPROVED, null, recipient, refId, "제목", "본문"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(statusOf(recipient)).isEqualTo("FAILED"));
    }

    @Test
    @DisplayName("같은 (template_type, ref_id, recipient_email) 재예약은 커밋 시점에 UNIQUE 위반이 된다")
    void 같은_dedup_키_재예약이면_커밋에서_UNIQUE_위반() {
        UUID refId = UUID.randomUUID();
        String recipient = "pipeline-dup-" + refId + "@test";

        tx.executeWithoutResult(t ->
                mailCommand.schedule(TemplateType.SIGNUP_APPROVED, null, recipient, refId, "제목", "본문"));

        assertThatThrownBy(() -> tx.executeWithoutResult(t ->
                mailCommand.schedule(TemplateType.SIGNUP_APPROVED, null, recipient, refId, "제목2", "본문2")))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 첫 행의 비동기 디스패치가 끝난 뒤 정리되도록 대기
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(statusOf(recipient)).isEqualTo("SENT"));
    }
}
