package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.onboarding.dto.CreateApplicationRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/**
 * 승인 한 번이 만드는 네 행 — 회사 · 기업 관리자 · 설정 토큰 · 예약 메일 (ON-04·06·07 · Q-33·34).
 *
 * <p><b>목으로는 성립하지 않는다.</b> {@code MemberCommand}·{@code InitialPasswordSetup}·
 * {@code MailCommand}를 전부 스텁하면 남는 것은 "그 셋을 불렀다"뿐이고, 그건 협력 구조지
 * 결과가 아니다 (tests.md §3-6). 승인이 진짜로 성립했는지는 네 테이블의 행으로만 확인된다.
 *
 * <p>한 트랜잭션이라는 것도 여기서만 보인다 — 중간에 끊기면 회사는 있는데 관리자가 없거나,
 * 계정은 있는데 비밀번호를 정할 링크가 없는 상태가 남는다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 서비스가 호출자 트랜잭션에 합류해
 * 커밋이 미뤄지고 JdbcTemplate이 확정 전 상태를 읽는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApplicationApproveIntegrationTest {

    private final String 사업자번호 = UUID.randomUUID().toString().substring(0, 13);
    private final String 이메일 = "seoyeon-" + UUID.randomUUID() + "@hanbit.co.kr";

    @Autowired private ApplicationService applicationService;
    @Autowired private ApplicationAdminService applicationAdminService;
    @Autowired private JdbcTemplate jdbc;

    private UUID 신청_ID;

    @BeforeEach
    void 김서연이_신청한다() {
        신청_ID = applicationService.submit(new CreateApplicationRequest(
                "한빛오피스", 사업자번호, 이메일, "김서연")).id();
    }

    /** FK 방향의 역순으로 지운다 — email_log · password_reset_token → member → company → application */
    @AfterEach
    void 심은_행을_지운다() {
        jdbc.update("delete from email_log where recipient_email = ?", 이메일);
        jdbc.update("""
                delete from password_reset_token
                where member_id in (select id from member where lower(email) = ?)
                """, 이메일);
        jdbc.update("delete from member where lower(email) = ?", 이메일);
        jdbc.update("delete from company where application_id = ?", 신청_ID);
        jdbc.update("delete from application where id = ?", 신청_ID);
    }

    /**
     * ON-04·07 — 승인의 효과는 상태 전이 하나가 아니다. 회사가 생기고 그 회사의 첫
     * 기업 관리자가 함께 생긴다. 관리자 없는 회사가 남으면 MB-11이 처음부터 깨진다.
     */
    @Test
    void 승인하면_회사와_기업_관리자_계정이_함께_생긴다() {
        // when — 플랫폼 관리자가 승인하면
        applicationAdminService.approve(신청_ID);

        // then — 신청은 승인으로 종결되고
        assertThat(문자열("select status from application where id = ?", 신청_ID))
                .isEqualTo("APPROVED");

        // then — 그 신청서를 근거로 회사가 하나 생긴다
        Map<String, Object> 회사 = jdbc.queryForMap(
                "select id, name, business_no, status from company where application_id = ?", 신청_ID);
        assertThat(회사.get("name")).isEqualTo("한빛오피스");
        assertThat(회사.get("business_no")).isEqualTo(사업자번호);
        assertThat(회사.get("status")).isEqualTo("ACTIVE");

        // then — 그 회사의 구성원은 김서연 한 명, 역할은 기업 관리자다
        Map<String, Object> 구성원 = jdbc.queryForMap("""
                select company_id, name, role, status, password_hash
                from member where lower(email) = ?
                """, 이메일);
        assertThat(구성원.get("company_id")).isEqualTo(회사.get("id"));
        assertThat(구성원.get("name")).isEqualTo("김서연");
        assertThat(구성원.get("role")).isEqualTo("COMPANY_ADMIN");
        assertThat(구성원.get("status")).isEqualTo("ACTIVE");

        // then — 비밀번호는 아직 없다 (Q-33). 링크로 본인이 채운다
        assertThat(구성원.get("password_hash")).isNull();
    }

    /**
     * NT-13 · Q-33·34 — 승인 통보가 곧 비밀번호 설정 링크다. 토큰이 실제로
     * {@code INITIAL_SETUP}으로 발급돼야 30분이 아닌 7일을 산다.
     *
     * <p>메일 본문은 확인하지 않는다 — {@code email_log}에 저장하지 않기로 한 값이다
     * (14 §2-1). 링크가 나갔다는 것은 예약 행과 토큰 행으로 확인한다.
     */
    @Test
    void 승인_메일은_비밀번호_설정_링크를_담고_7일_유효하다() {
        // given — 승인 직전 시각. 만료를 이 시각 기준으로 잰다
        Instant 승인_전 = Instant.now();

        // when — 승인하면
        applicationAdminService.approve(신청_ID);

        // then — 최초 설정 목적의 토큰이 활성 상태로 하나 발급된다
        Map<String, Object> 토큰 = jdbc.queryForMap("""
                select purpose, status, expires_at from password_reset_token
                where member_id = (select id from member where lower(email) = ?)
                """, 이메일);
        assertThat(토큰.get("purpose")).isEqualTo("INITIAL_SETUP");
        assertThat(토큰.get("status")).isEqualTo("ACTIVE");

        // then — 수명은 7일이다. 재설정(30분)과 갈리는 지점이 여기다
        Instant 만료 = ((java.sql.Timestamp) 토큰.get("expires_at")).toInstant();
        assertThat(ChronoUnit.HOURS.between(승인_전, 만료)).isBetween(167L, 168L);

        // then — 승인 통보 메일이 신청자 앞으로 예약된다. 식별자는 신청서 id다
        Map<String, Object> 메일 = jdbc.queryForMap("""
                select template_type, ref_type, ref_id from email_log where recipient_email = ?
                """, 이메일);
        assertThat(메일.get("template_type")).isEqualTo("SIGNUP_APPROVED");
        assertThat(메일.get("ref_type")).isEqualTo("APPLICATION");
        assertThat(메일.get("ref_id")).isEqualTo(신청_ID);
    }

    private String 문자열(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
}
