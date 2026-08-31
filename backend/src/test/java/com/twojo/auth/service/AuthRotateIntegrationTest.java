package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.auth.token.SecureTokenFactory;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 회전 실패 경로의 폐기가 <b>DB에 남는가</b> — 실제 PostgreSQL(twojo_test)에서 확인한다.
 *
 * <p>목 기반 단위 테스트로는 검증할 수 없다. 목은 자바 객체의 필드 변경만 보여주는데
 * 이 결함은 "그 변경이 커밋됐는가"에 있기 때문이다. AuthServiceTest의 같은 이름 케이스는
 * rotate()가 롤백되던 시절에도 초록불이었다.
 *
 * <p><b>@Transactional을 붙이지 않는다.</b> 붙이면 테스트가 바깥 트랜잭션을 열어
 * 커밋 여부라는 관측 대상 자체를 뒤튼다. 대신 @AfterEach로 심은 행을 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuthRotateIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");

    @Autowired private AuthService authService;
    @Autowired private SecureTokenFactory secureTokenFactory;
    @Autowired private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;

    /** application → company → member 순으로 심는다 (FK 방향). 값은 실행마다 달라 잔여물과 충돌하지 않는다. */
    @BeforeEach
    void 시드를_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);
        String email = "it-" + memberId + "@twojo.test";

        jdbc.update("""
                insert into application (id, company_name, business_no, email, status)
                values (?, ?, ?, ?, 'APPROVED')
                """, applicationId, "통합테스트상사", businessNo, email);
        jdbc.update("""
                insert into company (id, application_id, name, business_no, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, companyId, applicationId, "통합테스트상사", businessNo);
        jdbc.update("""
                insert into member (id, company_id, email, name, role, status)
                values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')
                """, memberId, companyId, email, "통합테스트");
    }

    @AfterEach
    void 시드를_지운다() {
        jdbc.update("delete from refresh_token where member_id = ?", memberId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    @Test
    void 회전된_토큰을_다시_쓰면_다른_기기_세션이_DB에서도_폐기된다() {
        String reusedRaw = secureTokenFactory.generate();
        insertToken(reusedRaw, "REVOKED", "ROTATED");

        String otherDeviceRaw = secureTokenFactory.generate();
        UUID otherDeviceId = insertToken(otherDeviceRaw, "ACTIVE", null);

        assertThatThrownBy(() -> authService.rotate(reusedRaw, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);

        // 트랜잭션이 끝난 뒤 DB를 직접 읽는다 — 롤백됐다면 여기서 ACTIVE가 나온다
        assertThat(columnOf(otherDeviceId, "status")).isEqualTo("REVOKED");
        assertThat(columnOf(otherDeviceId, "revoked_reason")).isEqualTo("REUSE_DETECTED");
    }

    /**
     * 이 경로만 FOR UPDATE로 잠근 그 행을 자기가 다시 UPDATE한다.
     * 폐기를 별도 트랜잭션(REQUIRES_NEW)으로 분리했다면 여기서 무한 대기한다 —
     * 락 대기 사이클이 아니라 PostgreSQL이 데드락으로 끊어 주지도 않는다.
     * 그래서 타임아웃을 별도 스레드로 건다: 멈추면 빌드가 매달리는 대신 10초 만에 실패한다.
     */
    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void 비활성_구성원이_재발급하면_남은_세션이_DB에서도_폐기된다() {
        jdbc.update("update member set status = 'INACTIVE' where id = ?", memberId);

        String raw = secureTokenFactory.generate();
        UUID tokenId = insertToken(raw, "ACTIVE", null);

        assertThatThrownBy(() -> authService.rotate(raw, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);

        assertThat(columnOf(tokenId, "status")).isEqualTo("REVOKED");
        assertThat(columnOf(tokenId, "revoked_reason")).isEqualTo("MEMBER_DEACTIVATED");
    }

    private UUID insertToken(String rawToken, String status, String revokedReason) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into refresh_token
                    (id, actor_type, member_id, token_hash, status, revoked_reason, expires_at)
                values (?, 'MEMBER', ?, ?, ?, ?, ?)
                """,
                id, memberId, secureTokenFactory.hash(rawToken), status, revokedReason,
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));
        return id;
    }

    /** JPA를 거치지 않고 원본 행을 읽는다 — 영속성 컨텍스트가 답을 대신 만들어 주지 않게. */
    private String columnOf(UUID tokenId, String column) {
        return jdbc.queryForObject(
                "select " + column + " from refresh_token where id = ?", String.class, tokenId);
    }
}
