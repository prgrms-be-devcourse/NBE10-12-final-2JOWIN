package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.member.dto.UpdateMeRequest;
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
 * 프로필 수정이 DB에 닿는가 (AU-07).
 *
 * <p><b>목으로는 성립하지 않는다.</b> MeService 클래스에는 @Transactional(readOnly = true)가
 * 걸려 있다. update에서 그것을 덮지 않으면 읽기 전용 트랜잭션이 변경을 밀어 넣지 않는데,
 * 영속성 컨텍스트의 엔티티는 실제로 바뀌어 응답에는 새 값이 담긴다 — 200이 나가고
 * 화면상 성공처럼 보이는 실패가 된다. 단위 테스트는 이 차이를 만들 수 없다.
 *
 * <p><b>@Transactional을 붙이지 않는다.</b> 붙이면 서비스가 호출자 트랜잭션에 합류해 커밋이
 * 미뤄지고, JdbcTemplate이 확정 전 상태를 읽는다 (PasswordResetIntegrationTest와 같은 이유).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MeServiceIntegrationTest {

    @Autowired private MeService meService;
    @Autowired private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;

    @BeforeEach
    void 구성원을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);
        String 이메일 = "jihun-" + memberId + "@twojo.test";

        jdbc.update("""
                insert into application (id, company_name, business_no, email, status)
                values (?, ?, ?, ?, 'APPROVED')
                """, applicationId, "한빛오피스", businessNo, 이메일);
        jdbc.update("""
                insert into company (id, application_id, name, business_no, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("""
                insert into member (id, company_id, email, name, phone, role, status)
                values (?, ?, ?, ?, ?, 'SALES_REP', 'ACTIVE')
                """, memberId, companyId, 이메일, "박지훈", "010-2000-0002");
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /** 07 §A PATCH /api/v1/me — 응답이 새 값이어도 DB가 옛 값이면 수정된 것이 아니다. */
    @Test
    void 프로필_수정은_DB에_반영된다() {
        // given — 박지훈의 연락처가 010-2000-0002로 저장돼 있다
        AccessContext 박지훈 =
                new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);

        // when — 새 연락처로 프로필을 수정하면
        meService.update(박지훈, new UpdateMeRequest("박지훈", "010-9999-0002"));

        // then — 응답이 아니라 행 자체가 바뀌어 있다
        assertThat(연락처()).isEqualTo("010-9999-0002");
    }

    /** JPA를 거치지 않고 원본 행을 읽는다 — 영속성 컨텍스트가 답을 대신 만들어 주지 않게. */
    private String 연락처() {
        return jdbc.queryForObject(
                "select phone from member where id = ?", String.class, memberId);
    }
}
