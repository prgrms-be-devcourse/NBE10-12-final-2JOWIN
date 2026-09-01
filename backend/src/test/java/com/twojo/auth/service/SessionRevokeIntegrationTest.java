package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.auth.SessionRevoker;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * 회사 정지 폐기의 테넌트 격리 (SC-01 · ON-08·09).
 *
 * <p>목으로는 성립하지 않는다 — findAllActive를 스텁하는 순간 "무엇이 돌아오는가"를
 * 테스트가 정해주고, 격리를 실제로 수행하는 where company_id = ? 가 검증 경로에서 사라진다.
 *
 * <p><b>@Transactional을 붙이지 않는다.</b> 붙이면 더티 체킹이 아직 UPDATE를 날리지 않은
 * 상태에서 JdbcTemplate이 raw SQL을 쏴 바뀌기 전 값을 읽는다 (AuthRotateIntegrationTest와 같은 이유).
 * 대신 @AfterEach로 심은 행을 직접 지운다.
 *
 * <p>픽스처가 AuthRotateIntegrationTest·SecurityChainIntegrationTest와 겹친다.
 * SC-01 테스트가 하나 더 생기면 그때 공통 헬퍼로 뽑는다 — 둘을 위해 추상화하기엔 이르다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionRevokeIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T16:00:00Z");

    @Autowired private SessionRevoker sessionRevoker;
    @Autowired private JdbcTemplate jdbc;

    private CompanySet 회사A;
    private CompanySet 회사B;

    @BeforeEach
    void 두_회사를_심는다() {
        회사A = 회사를_심는다("한빛오피스", "김서연");
        회사B = 회사를_심는다("도담테크", "박지훈");
    }

    @AfterEach
    void 모두_지운다() {
        지운다(회사B);
        지운다(회사A);
    }

    /** SC-01 — 정지의 효과가 회사 경계를 넘으면 무관한 회사 전원이 로그아웃된다 */
    @Test
    void 회사를_정지해도_다른_회사_구성원의_세션은_유지된다() {
        // when — 회사 A 만 정지시키면
        sessionRevoker.revokeOnSuspension(회사A.companyId(), NOW);

        // then — A 는 실제로 끊긴다. 이 단정이 없으면 아래가 공허해진다(아무 일도 안 해도 통과한다)
        assertThat(상태(회사A.tokenId())).isEqualTo("REVOKED");

        // then — B 는 손대지 않는다. 이것이 이 테스트의 본체다
        assertThat(상태(회사B.tokenId())).isEqualTo("ACTIVE");
    }

    /** application → company → member → refresh_token 순으로 심는다 (FK 방향). */
    private CompanySet 회사를_심는다(String 회사명, String 구성원명) {
        UUID applicationId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);
        String email = "revoke-" + memberId + "@twojo.test";

        jdbc.update("""
                insert into application (id, company_name, business_no, email, status)
                values (?, ?, ?, ?, 'APPROVED')
                """, applicationId, 회사명, businessNo, email);
        jdbc.update("""
                insert into company (id, application_id, name, business_no, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, companyId, applicationId, 회사명, businessNo);
        jdbc.update("""
                insert into member (id, company_id, email, name, role, status)
                values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')
                """, memberId, companyId, email, 구성원명);
        jdbc.update("""
                insert into refresh_token (id, actor_type, member_id, token_hash, status, expires_at)
                values (?, 'MEMBER', ?, ?, 'ACTIVE', ?)
                """, tokenId, memberId, "hash-" + tokenId,
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));

        return new CompanySet(applicationId, companyId, memberId, tokenId);
    }

    private void 지운다(CompanySet 회사) {
        jdbc.update("delete from refresh_token where member_id = ?", 회사.memberId());
        jdbc.update("delete from member where id = ?", 회사.memberId());
        jdbc.update("delete from company where id = ?", 회사.companyId());
        jdbc.update("delete from application where id = ?", 회사.applicationId());
    }

    /** JPA를 거치지 않고 원본 행을 읽는다 — 영속성 컨텍스트가 답을 대신 만들어 주지 않게. */
    private String 상태(UUID tokenId) {
        return jdbc.queryForObject(
                "select status from refresh_token where id = ?", String.class, tokenId);
    }

    private record CompanySet(UUID applicationId, UUID companyId, UUID memberId, UUID tokenId) {}
}
