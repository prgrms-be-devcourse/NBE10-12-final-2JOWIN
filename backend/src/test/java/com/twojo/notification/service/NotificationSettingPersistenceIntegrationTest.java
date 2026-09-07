package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.twojo.boundary.NotificationSettingCommand;
import com.twojo.boundary.NotificationSettingQuery;
import com.twojo.boundary.NotificationSettingType;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실 PG로만 잡히는 것 (NT-07):
 * <ul>
 *   <li>{@code settingsOf}가 저장 행이 없어도 4종을 채워 반환하고, 저장된 값을 그대로 읽는다.</li>
 *   <li>{@code replaceSettings}의 네이티브 upsert가 {@code (member_id, type)}를 갈아끼운다 —
 *       재PUT에도 {@code uk_notification_setting} 위반 없이 행이 4개로 유지된다.</li>
 *   <li>{@code replaceSettings}가 트랜잭션 밖 호출에도 자체 트랜잭션으로 커밋한다({@code REQUIRED}).</li>
 * </ul>
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 서비스가 테스트 트랜잭션에 합류해 커밋이
 * 미뤄지고 {@link JdbcTemplate}이 확정 전 상태를 읽는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationSettingPersistenceIntegrationTest {

    @Autowired
    private NotificationSettingQuery query;
    @Autowired
    private NotificationSettingCommand command;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager txManager;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;

    @BeforeEach
    void 구성원을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);
        String email = "setting-" + memberId + "@twojo.test";

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status)"
                + " values (?, ?, ?, ?, '김서연', 'APPROVED')", applicationId, "한빛오피스", businessNo, email);
        jdbc.update("insert into company (id, application_id, name, business_no, status)"
                + " values (?, ?, ?, ?, 'ACTIVE')", companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status)"
                + " values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')", memberId, companyId, email, "김서연");
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from notification_setting where member_id = ?", memberId);
        jdbc.update("delete from member where company_id = ?", companyId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    private int rowCount() {
        return jdbc.queryForObject(
                "select count(*) from notification_setting where member_id = ?", Integer.class, memberId);
    }

    private static Map<NotificationSettingType, Boolean> settings(
            boolean viewed, boolean responded, boolean remind, boolean inquiry) {
        Map<NotificationSettingType, Boolean> m = new EnumMap<>(NotificationSettingType.class);
        m.put(NotificationSettingType.QUOTE_VIEWED, viewed);
        m.put(NotificationSettingType.QUOTE_RESPONDED, responded);
        m.put(NotificationSettingType.REMIND_NO_RESPONSE, remind);
        m.put(NotificationSettingType.INQUIRY_RECEIVED, inquiry);
        return m;
    }

    @Test
    void settingsOf는_저장_행이_없으면_4종_전부_ON을_준다() {
        Map<NotificationSettingType, Boolean> result = query.settingsOf(memberId);

        assertThat(result).hasSize(4);
        assertThat(result.values()).containsOnly(true);
    }

    @Test
    void replaceSettings_후_settingsOf가_저장한_값을_그대로_읽는다() {
        new TransactionTemplate(txManager).executeWithoutResult(t ->
                command.replaceSettings(memberId, settings(false, true, true, false)));

        Map<NotificationSettingType, Boolean> result = query.settingsOf(memberId);

        assertThat(result.get(NotificationSettingType.QUOTE_VIEWED)).isFalse();
        assertThat(result.get(NotificationSettingType.QUOTE_RESPONDED)).isTrue();
        assertThat(result.get(NotificationSettingType.REMIND_NO_RESPONSE)).isTrue();
        assertThat(result.get(NotificationSettingType.INQUIRY_RECEIVED)).isFalse();
        assertThat(rowCount()).isEqualTo(4);
    }

    @Test
    void 재PUT은_덮어쓰고_행은_4개로_유지된다() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(t -> command.replaceSettings(memberId, settings(false, false, false, false)));
        tx.executeWithoutResult(t -> command.replaceSettings(memberId, settings(true, true, true, true)));

        assertThat(query.settingsOf(memberId).values()).containsOnly(true);
        assertThat(rowCount()).isEqualTo(4);
    }

    @Test
    void replaceSettings는_트랜잭션_밖_호출에도_자체_커밋한다() {
        assertThatCode(() -> command.replaceSettings(memberId, settings(false, false, false, false)))
                .doesNotThrowAnyException();

        assertThat(query.settingsOf(memberId).values()).containsOnly(false);
        assertThat(rowCount()).isEqualTo(4);
    }
}
