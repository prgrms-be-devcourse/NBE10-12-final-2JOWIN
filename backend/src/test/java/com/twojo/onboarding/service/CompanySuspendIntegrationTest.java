package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.onboarding.dto.SuspendCompanyRequest;
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
 * 정지가 실제로 이용을 끊는가 (ON-08·09 · 전이표 §9 · Q-27).
 *
 * <p><b>상태 전이만으로는 아무도 못 막는다.</b> 이미 발급된 access token은 수명만큼 살아
 * 있고 refresh로 계속 갱신된다 — 폐기가 없으면 최대 14일간 정상 이용이고 ON-09가 무력해진다.
 * 그래서 이 테스트는 {@code company.status}가 아니라 {@code refresh_token.status}를 본다.
 *
 * <p><b>목으로는 성립하지 않는다.</b> {@code SessionRevoker}를 스텁하면 "불렀다"만 남고,
 * 어느 회사의 어느 행이 실제로 폐기됐는지가 사라진다. 회사 id를 잘못 넘기는 실수가
 * 목에서는 통과한다.
 *
 * <p>{@code SessionRevokeIntegrationTest}와 겹쳐 보이지만 층이 다르다. 그쪽은 폐기 자체의
 * 회사 축 격리를, 여기는 {@code CompanyAdminService}가 그 폐기를 올바른 회사로 부르는지를 본다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 더티 체킹이 UPDATE를 내보내기
 * 전에 JdbcTemplate이 바뀌기 전 값을 읽는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompanySuspendIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-07T09:00:00Z");

    @Autowired private CompanyAdminService companyAdminService;
    @Autowired private JdbcTemplate jdbc;

    private 회사 한빛오피스;
    private 회사 도담테크;

    @BeforeEach
    void 두_회사를_심는다() {
        한빛오피스 = 심는다("한빛오피스", "김서연");
        도담테크 = 심는다("도담테크", "박지훈");
    }

    @AfterEach
    void 모두_지운다() {
        지운다(도담테크);
        지운다(한빛오피스);
    }

    /** ON-08·09 — "구성원 전원 즉시 이용 차단"의 실체는 refresh 전 행 폐기다 */
    @Test
    void 회사를_정지하면_그_회사_구성원의_세션이_전부_폐기된다() {
        // when — 플랫폼 관리자가 한빛오피스를 정지시키면
        var 응답 = companyAdminService.suspend(
                한빛오피스.companyId(), new SuspendCompanyRequest("이용료 미납"));

        // then — 회사는 정지 상태로 사유와 함께 남고
        assertThat(응답.status()).isEqualTo("SUSPENDED");
        assertThat(응답.suspendReason()).isEqualTo("이용료 미납");

        // then — 김서연의 세션은 회사 정지를 사유로 폐기된다. 재발급 경로가 닫힌다
        assertThat(토큰_상태(한빛오피스)).isEqualTo("REVOKED");
        assertThat(폐기_사유(한빛오피스)).isEqualTo("COMPANY_SUSPENDED");
    }

    /** SC-01 — 정지의 효과가 회사 경계를 넘으면 무관한 회사 전원이 로그아웃된다 */
    @Test
    void 한_회사를_정지해도_다른_회사_구성원의_세션은_유지된다() {
        // when — 한빛오피스만 정지시키면
        companyAdminService.suspend(
                한빛오피스.companyId(), new SuspendCompanyRequest("이용료 미납"));

        // then — 한빛오피스는 실제로 끊긴다. 이 단정이 없으면 아래가 공허해진다
        assertThat(토큰_상태(한빛오피스)).isEqualTo("REVOKED");

        // then — 도담테크의 박지훈은 손대지 않는다. 이것이 이 테스트의 본체다
        assertThat(토큰_상태(도담테크)).isEqualTo("ACTIVE");
    }

    /**
     * Q-27 — "구성원은 재로그인 필요(정지 시 refresh가 폐기됐으므로)".
     *
     * <p>되살리면 정지 중에 탈취된 토큰까지 함께 산다. 폐기는 되돌리는 것이 아니라
     * 새 로그인으로 새 행을 얻는 것으로 해소된다.
     */
    @Test
    void 정지를_해제해도_폐기된_세션은_되살아나지_않는다() {
        // given — 한빛오피스가 정지되어 김서연의 세션이 끊긴 상태다
        companyAdminService.suspend(
                한빛오피스.companyId(), new SuspendCompanyRequest("이용료 미납"));

        // when — 플랫폼 관리자가 정지를 해제하면
        var 응답 = companyAdminService.reactivate(한빛오피스.companyId());

        // then — 회사는 운영 중으로 돌아가지만
        assertThat(응답.status()).isEqualTo("ACTIVE");
        assertThat(응답.suspendReason()).isNull();

        // then — 옛 세션은 폐기된 채다. 김서연은 다시 로그인해야 한다
        assertThat(토큰_상태(한빛오피스)).isEqualTo("REVOKED");
    }

    /** application → company → member → refresh_token 순으로 심는다 (FK 방향). */
    private 회사 심는다(String 회사명, String 구성원명) {
        UUID applicationId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);
        String email = "suspend-" + memberId + "@twojo.test";

        jdbc.update("""
                insert into application (id, company_name, business_no, email, applicant_name, status)
                values (?, ?, ?, ?, ?, 'APPROVED')
                """, applicationId, 회사명, businessNo, email, 구성원명);
        jdbc.update("""
                insert into company (id, application_id, name, business_no, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, companyId, applicationId, 회사명, businessNo);
        jdbc.update("""
                insert into member (id, company_id, email, name, role, status)
                values (?, ?, ?, ?, 'COMPANY_ADMIN', 'ACTIVE')
                """, memberId, companyId, email, 구성원명);
        jdbc.update("""
                insert into refresh_token (id, actor_type, member_id, token_hash, status, expires_at)
                values (?, 'MEMBER', ?, ?, 'ACTIVE', ?)
                """, tokenId, memberId, "hash-" + tokenId,
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofDays(14)), ZoneOffset.UTC));

        return new 회사(applicationId, companyId, memberId, tokenId);
    }

    private void 지운다(회사 대상) {
        jdbc.update("delete from refresh_token where member_id = ?", 대상.memberId());
        jdbc.update("delete from member where id = ?", 대상.memberId());
        jdbc.update("delete from company where id = ?", 대상.companyId());
        jdbc.update("delete from application where id = ?", 대상.applicationId());
    }

    /** JPA를 거치지 않고 원본 행을 읽는다 — 영속성 컨텍스트가 답을 대신 만들어 주지 않게. */
    private String 토큰_상태(회사 대상) {
        return jdbc.queryForObject(
                "select status from refresh_token where id = ?", String.class, 대상.tokenId());
    }

    private String 폐기_사유(회사 대상) {
        return jdbc.queryForObject(
                "select revoked_reason from refresh_token where id = ?", String.class, 대상.tokenId());
    }

    private record 회사(UUID applicationId, UUID companyId, UUID memberId, UUID tokenId) {}
}
