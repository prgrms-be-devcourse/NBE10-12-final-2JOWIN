package com.twojo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import java.sql.Timestamp;
import java.time.Instant;
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

/**
 * {@code markRead}/{@code markAllRead}가 실제 UPDATE를 낸다 (07 §D · NT-08).
 *
 * <p><b>목으로는 성립하지 않는다.</b> 서비스 쓰기 메서드에 {@code @Transactional}이 없으면 dirty checking이
 * flush되지 않아 204만 돌아가고 {@code read_at}은 그대로다 — 목 저장소에는 flush도 트랜잭션 경계도 없어
 * 그 실패가 안 잡힌다. 이 테스트만 빨간불이 된다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 서비스가 테스트 트랜잭션에 합류해 커밋이
 * 미뤄지고 {@link JdbcTemplate}이 확정 전 상태를 읽는다. 읽기도 JPA를 거치지 않는다 — 영속성 컨텍스트가
 * 답을 대신 만들어 주지 않게.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationPersistenceIntegrationTest {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private AccessContext ctx;

    @BeforeEach
    void 구성원을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);
        String email = "noti-" + memberId + "@twojo.test";

        jdbc.update("insert into application (id, company_name, business_no, email, status)"
                + " values (?, ?, ?, ?, 'APPROVED')", applicationId, "한빛오피스", businessNo, email);
        jdbc.update("insert into company (id, application_id, name, business_no, status)"
                + " values (?, ?, ?, ?, 'ACTIVE')", companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status)"
                + " values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')", memberId, companyId, email, "김서연");

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from notification where company_id = ?", companyId);
        jdbc.update("delete from member where company_id = ?", companyId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    private UUID 알림을_넣는다(UUID recipient, Instant readAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into notification"
                + " (id, company_id, recipient_member_id, type, message, ref_type, ref_id, read_at)"
                + " values (?, ?, ?, 'QUOTE_VIEWED', '고객이 견적을 열람했습니다', 'QUOTE', ?, ?)",
                id, companyId, recipient, UUID.randomUUID(),
                readAt == null ? null : Timestamp.from(readAt));
        return id;
    }

    private Instant readAt(UUID id) {
        Timestamp ts = jdbc.queryForObject(
                "select read_at from notification where id = ?", Timestamp.class, id);
        return ts == null ? null : ts.toInstant();
    }

    @Test
    void markRead가_read_at을_실제로_기록한다() {
        UUID id = 알림을_넣는다(memberId, null);

        notificationService.markRead(ctx, id);

        assertThat(readAt(id)).isNotNull();
    }

    @Test
    void markRead는_이미_읽은_알림의_시각을_바꾸지_않는다() {
        Instant first = Instant.parse("2026-09-01T00:00:00Z");
        UUID id = 알림을_넣는다(memberId, first);

        notificationService.markRead(ctx, id);

        assertThat(readAt(id)).isEqualTo(first);
    }

    @Test
    void markAllRead가_본인_미읽음_전체를_기록하고_읽은_것은_건드리지_않는다() {
        UUID a = 알림을_넣는다(memberId, null);
        UUID b = 알림을_넣는다(memberId, null);
        Instant alreadyRead = Instant.parse("2026-09-01T00:00:00Z");
        UUID c = 알림을_넣는다(memberId, alreadyRead);

        notificationService.markAllRead(ctx);

        assertThat(readAt(a)).isNotNull();
        assertThat(readAt(b)).isNotNull();
        assertThat(readAt(c)).isEqualTo(alreadyRead);
    }

    @Test
    void markRead는_다른_구성원의_알림을_건드리지_않는다() {
        UUID otherMember = UUID.randomUUID();
        jdbc.update("insert into member (id, company_id, email, name, role, status)"
                + " values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')",
                otherMember, companyId, "other-" + otherMember + "@twojo.test", "이도현");
        UUID id = 알림을_넣는다(otherMember, null);

        assertThatThrownBy(() -> notificationService.markRead(ctx, id))
                .isInstanceOf(BusinessException.class);
        assertThat(readAt(id)).isNull();
    }
}
