package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.auth.dto.LoginRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 관리자 세션이 DB에 남기는 것 — 실제 PostgreSQL(twojo_test)에서 확인한다.
 *
 * <p>둘 다 목으로는 검증할 수 없다. 잠금 판정은 조회에 actor_type 조건이 있어야 성립하고,
 * 재사용 폐기는 "그 변경이 커밋됐는가"가 검증 대상이라 자바 객체의 필드 변경만으로는 부족하다.
 *
 * <p><b>@Transactional을 붙이지 않는다.</b> 붙이면 테스트가 바깥 트랜잭션을 열어
 * 커밋 여부라는 관측 대상 자체를 뒤튼다. 대신 @AfterEach로 심은 행을 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AdminAuthIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
    private static final String PASSWORD = "test1234!";
    private static final String IP = "127.0.0.1";

    @Autowired private AdminAuthService adminAuthService;
    @Autowired private AuthService authService;
    @Autowired private SecureTokenFactory secureTokenFactory;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private String email;
    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID adminId;

    /**
     * 같은 이메일을 구성원과 관리자가 하나씩 갖는다 — 두 표가 따로라 가능한 상황이다 (Q-30).
     * 값은 실행마다 달라 잔여물과 충돌하지 않는다.
     */
    @BeforeEach
    void 시드를_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        email = "admin-it-" + adminId + "@twojo.test";
        String businessNo = applicationId.toString().substring(0, 13);
        String hash = passwordEncoder.encode(PASSWORD);

        jdbc.update("""
                insert into application (id, company_name, business_no, email, status)
                values (?, ?, ?, ?, 'APPROVED')
                """, applicationId, "통합테스트상사", businessNo, email);
        jdbc.update("""
                insert into company (id, application_id, name, business_no, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, companyId, applicationId, "통합테스트상사", businessNo);
        jdbc.update("""
                insert into member (id, company_id, email, password_hash, name, role, status)
                values (?, ?, ?, ?, ?, 'SALES_REP', 'ACTIVE')
                """, memberId, companyId, email, hash, "통합테스트");
        jdbc.update("""
                insert into platform_admin (id, email, password_hash, status)
                values (?, ?, ?, 'ACTIVE')
                """, adminId, email, hash);
    }

    @AfterEach
    void 시드를_지운다() {
        jdbc.update("delete from login_attempt where email = ?", email);
        jdbc.update("delete from refresh_token where platform_admin_id = ?", adminId);
        jdbc.update("delete from refresh_token where member_id = ?", memberId);
        jdbc.update("delete from platform_admin where id = ?", adminId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /** Q-30 — login_attempt 를 actor_type 으로 가르지 않으면 관리자 실패가 구성원을 잠근다 */
    @Test
    void 관리자의_로그인_실패는_같은_이메일_구성원을_잠그지_않는다() {
        // given — 관리자 계정으로 5회 연속 틀린다 (AU-09 잠금 횟수)
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> adminAuthService.login(
                    new LoginRequest(email, "틀린-비밀번호", false), IP, NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
        }

        // then — 관리자 쪽은 실제로 잠긴다. 이 단정이 없으면 아래가 공허해진다
        assertThatThrownBy(() -> adminAuthService.login(
                new LoginRequest(email, PASSWORD, false), IP, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_LOCKED);

        // then — 같은 이메일의 구성원은 그대로 로그인된다. 이것이 이 테스트의 본체다
        assertThatCode(() -> authService.login(
                new LoginRequest(email, PASSWORD, false), IP, NOW))
                .doesNotThrowAnyException();
    }

    /** 05 §9 — noRollbackFor 가 없으면 이 폐기가 예외와 함께 사라진다 */
    @Test
    void 관리자가_회전된_토큰을_다시_쓰면_다른_기기_세션이_DB에서도_폐기된다() {
        // given — 이미 한 번 쓰인 토큰과, 아직 살아 있는 다른 기기 세션
        String 재사용_원문 = secureTokenFactory.generate();
        insertToken(재사용_원문, "REVOKED", "ROTATED");
        String 다른_기기_원문 = secureTokenFactory.generate();
        UUID 다른_기기_id = insertToken(다른_기기_원문, "ACTIVE", null);

        // when — 회전된 원문이 다시 제시되면
        assertThatThrownBy(() -> adminAuthService.rotate(재사용_원문, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_ACTIVE);

        // then — 트랜잭션이 끝난 뒤 DB를 직접 읽는다. 롤백됐다면 여기서 ACTIVE 가 나온다
        assertThat(columnOf(다른_기기_id, "status")).isEqualTo("REVOKED");
        assertThat(columnOf(다른_기기_id, "revoked_reason")).isEqualTo("REUSE_DETECTED");
    }

    private UUID insertToken(String rawToken, String status, String revokedReason) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into refresh_token
                    (id, actor_type, platform_admin_id, token_hash, status, revoked_reason,
                     expires_at)
                values (?, 'PLATFORM_ADMIN', ?, ?, ?, ?, ?)
                """, id, adminId, secureTokenFactory.hash(rawToken), status, revokedReason,
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));
        return id;
    }

    /** JPA를 거치지 않고 원본 행을 읽는다 — 영속성 컨텍스트가 답을 대신 만들어 주지 않게. */
    private String columnOf(UUID tokenId, String column) {
        return jdbc.queryForObject(
                "select " + column + " from refresh_token where id = ?", String.class, tokenId);
    }
}
