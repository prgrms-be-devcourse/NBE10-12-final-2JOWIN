package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.auth.dto.RequestPasswordResetRequest;
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
 * 재설정 재요청 시 "활성 1개 유지" (05 §10 · uk_password_reset_token_active).
 *
 * <p><b>목으로는 성립하지 않는다.</b> Hibernate는 flush할 때 INSERT를 UPDATE보다 먼저
 * 내보낸다. 그래서 만료 UPDATE와 발급 INSERT가 같은 flush에 묶이면, 기존 행이 아직
 * ACTIVE인 상태로 새 ACTIVE가 들어가 부분 유니크 인덱스에 걸린다.
 *
 * <p>PasswordService의 flush() 한 줄을 지워도 단위 테스트는 전부 초록불이다 —
 * 목 저장소에는 인덱스도 flush 순서도 없기 때문이다. 이 테스트만 빨간불이 된다.
 *
 * <p><b>@Transactional을 붙이지 않는다.</b> 붙이면 서비스가 호출자 트랜잭션에 합류해
 * 커밋이 미뤄지고, JdbcTemplate이 확정 전 상태를 읽는다 (SessionRevokeIntegrationTest와 같은 이유).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PasswordResetIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-02T11:20:33Z");

    @Autowired private PasswordService passwordService;
    @Autowired private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private String 이메일;

    @BeforeEach
    void 구성원을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        이메일 = "reset-" + memberId + "@twojo.test";
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("""
                insert into application (id, company_name, business_no, email, status)
                values (?, ?, ?, ?, 'APPROVED')
                """, applicationId, "한빛오피스", businessNo, 이메일);
        jdbc.update("""
                insert into company (id, application_id, name, business_no, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("""
                insert into member (id, company_id, email, name, role, status)
                values (?, ?, ?, ?, 'COMPANY_ADMIN', 'ACTIVE')
                """, memberId, companyId, 이메일, "김서연");
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from password_reset_token where member_id = ?", memberId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /** 05 §10 — "기존 활성 행은 만료됨(EXPIRED) — 활성 1개 유지" */
    @Test
    void 재설정을_다시_요청하면_활성_토큰은_하나만_남는다() {
        // given — 김서연이 재설정을 한 번 요청해 활성 토큰이 하나 있다
        passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW);
        assertThat(개수("ACTIVE")).isEqualTo(1);

        // when — 메일을 못 받아 1분 뒤 다시 요청하면
        passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW.plusSeconds(60));

        // then — 새 링크만 살아 있고, 이전 링크는 이력으로 남는다
        assertThat(개수("ACTIVE")).isEqualTo(1);
        assertThat(개수("EXPIRED")).isEqualTo(1);
    }

    /** JPA를 거치지 않고 원본 행을 센다 — 영속성 컨텍스트가 답을 대신 만들어 주지 않게. */
    private Integer 개수(String status) {
        return jdbc.queryForObject(
                "select count(*) from password_reset_token where member_id = ? and status = ?",
                Integer.class, memberId, status);
    }
}
