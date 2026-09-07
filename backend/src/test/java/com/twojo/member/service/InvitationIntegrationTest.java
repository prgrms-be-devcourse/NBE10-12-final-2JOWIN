package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.auth.dto.LoginRequest;
import com.twojo.auth.service.AuthService;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.member.dto.AcceptInvitationRequest;
import com.twojo.member.dto.CreateInvitationRequest;
import com.twojo.member.token.InvitationTokenGenerator;
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
 * 초대의 대기 행은 언제나 하나다 (Q-31 · uk_invitation_pending).
 *
 * <p><b>목으로는 성립하지 않는다.</b> Hibernate는 flush할 때 INSERT를 UPDATE보다 먼저
 * 내보낸다. 그래서 만료 UPDATE와 발급 INSERT가 같은 flush에 묶이면, 옛 행이 아직 PENDING인
 * 상태로 새 PENDING이 들어가 부분 유니크 인덱스에 걸린다.
 *
 * <p>InvitationService의 flush() 두 줄을 지워도 단위 테스트는 전부 초록불이다 — 목 저장소에는
 * 인덱스도 flush 순서도 없기 때문이다. 이 테스트만 빨간불이 된다.
 *
 * <p>수락은 실제 인코더를 쓴다. 막으려는 위험(저장된 해시로 로그인이 안 됨)이 라이브러리 안에
 * 있어, 목으로 바꾸면 우리가 정한 답을 우리가 확인하게 된다 (저널 09-02).
 *
 * <p><b>@Transactional을 붙이지 않는다.</b> 붙이면 서비스가 호출자 트랜잭션에 합류해 커밋이
 * 미뤄지고, JdbcTemplate이 확정 전 상태를 읽는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InvitationIntegrationTest {

    private static final String 원문토큰 = "kJ9xQm2LinkTokenForAcceptTest";

    @Autowired private InvitationService invitationService;
    @Autowired private PublicInvitationService publicInvitationService;
    @Autowired private AuthService authService;
    @Autowired private InvitationTokenGenerator tokenGenerator;
    @Autowired private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID 김서연;
    private AccessContext 관리자;
    private String 초대이메일;

    @BeforeEach
    void 회사와_관리자를_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        김서연 = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);
        String 관리자이메일 = "seoyeon-" + 김서연 + "@twojo.test";
        초대이메일 = "newbie-" + 김서연 + "@twojo.test";

        jdbc.update("""
                insert into application (id, company_name, business_no, email, status)
                values (?, ?, ?, ?, 'APPROVED')
                """, applicationId, "한빛오피스", businessNo, 관리자이메일);
        jdbc.update("""
                insert into company (id, application_id, name, business_no, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("""
                insert into member (id, company_id, email, name, role, status)
                values (?, ?, ?, ?, 'COMPANY_ADMIN', 'ACTIVE')
                """, 김서연, companyId, 관리자이메일, "김서연");

        관리자 = new AccessContext(companyId, 김서연, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from refresh_token where member_id in (select id from member where company_id = ?)",
                companyId);
        jdbc.update("delete from login_attempt where email = ?", 초대이메일);
        jdbc.update("delete from invitation where company_id = ?", companyId);
        jdbc.update("delete from member where company_id = ?", companyId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /** 05 §3 재발송 — "기존 초대 EXPIRED(RESENT) 종결 + 새 토큰 발급(새 행)" */
    @Test
    void 재발송해도_대기_초대는_하나뿐이다() {
        // given — 김서연이 신입을 한 번 불러 대기 초대가 하나 있다
        UUID 첫_초대 = invitationService
                .create(관리자, new CreateInvitationRequest(초대이메일, "SALES_REP")).id();
        assertThat(개수("PENDING")).isEqualTo(1);

        // when — 메일을 못 받았다고 해서 다시 보내면
        invitationService.resend(관리자, 첫_초대);

        // then — 새 링크만 살아 있고, 이전 링크는 이력으로 남는다
        assertThat(개수("PENDING")).isEqualTo(1);
        assertThat(개수("EXPIRED")).isEqualTo(1);
    }

    /** MB-04 — 만료 배치가 없어 발송 자리가 곧 만료 시점이다. 넘기지 않으면 그 이메일이 영구히 막힌다. */
    @Test
    void 기한이_지난_대기_초대가_있으면_새_초대가_발급된다() {
        // given — 8일 전에 보낸 초대가 아직 PENDING인 채로 남아 있다
        jdbc.update("""
                insert into invitation (id, company_id, invited_by_member_id, email, role,
                                        token_hash, status, expires_at)
                values (?, ?, ?, ?, 'SALES_REP', ?, 'PENDING', now() - interval '1 day')
                """, UUID.randomUUID(), companyId, 김서연, 초대이메일, "stale-" + UUID.randomUUID());

        // when — 김서연이 같은 사람을 다시 부르면
        invitationService.create(관리자, new CreateInvitationRequest(초대이메일, "SALES_REP"));

        // then — 죽은 행은 만료로 넘어가고 새 대기 행이 그 자리를 받는다
        assertThat(개수("PENDING")).isEqualTo(1);
        assertThat(개수("EXPIRED")).isEqualTo(1);
    }

    /** MB-03 · AU-01 — 수락으로 만든 계정은 그 비밀번호로 실제 로그인이 되어야 계정이다. */
    @Test
    void 수락한_계정의_비밀번호로_로그인할_수_있다() {
        // given — 김서연이 보낸 초대의 링크를 신입이 들고 있다
        jdbc.update("""
                insert into invitation (id, company_id, invited_by_member_id, email, role,
                                        token_hash, status, expires_at)
                values (?, ?, ?, ?, 'SALES_REP', ?, 'PENDING', now() + interval '7 days')
                """, UUID.randomUUID(), companyId, 김서연, 초대이메일, tokenGenerator.hash(원문토큰));

        // when — 이름과 비밀번호를 넣어 수락하고, 방금 정한 비밀번호로 로그인하면
        publicInvitationService.accept(원문토큰, new AcceptInvitationRequest("한지민", "test1234!"));

        var 결과 = authService.login(
                new LoginRequest(초대이메일, "test1234!", false), "127.0.0.1", Instant.now());

        // then — 초대에 박혀 있던 역할로 들어온다. 수락자가 고른 것이 아니다
        assertThat(결과.response().role()).isEqualTo(Role.SALES_REP.name());
    }

    /** JPA를 거치지 않고 원본 행을 센다 — 영속성 컨텍스트가 답을 대신 만들어 주지 않게. */
    private Integer 개수(String status) {
        return jdbc.queryForObject(
                "select count(*) from invitation where company_id = ? and status = ?",
                Integer.class, companyId, status);
    }
}
