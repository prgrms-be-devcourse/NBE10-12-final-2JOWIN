package com.twojo.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.twojo.auth.jwt.JwtProvider;
import com.twojo.boundary.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 체인 3분리가 실제로 갈라지는가 (07 §경로 체계 · ON-11).
 *
 * <p>목으로는 검증할 수 없다 — 검증 대상이 로직이 아니라 <b>보안 설정 자체</b>다.
 * 같은 토큰 하나로 구성원 경로는 열리고 관리자 경로는 닫히는 것을 함께 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SecurityChainIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private String 구성원_토큰;

    /** application → company → member 순으로 심는다 (FK 방향). */
    @BeforeEach
    void 시드를_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);
        String email = "chain-" + memberId + "@twojo.test";

        jdbc.update("""
                insert into application (id, company_name, business_no, email, status)
                values (?, ?, ?, ?, 'APPROVED')
                """, applicationId, "한빛오피스", businessNo, email);
        jdbc.update("""
                insert into company (id, application_id, name, business_no, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("""
                insert into member (id, company_id, email, name, phone, role, status)
                values (?, ?, ?, ?, ?, 'COMPANY_ADMIN', 'ACTIVE')
                """, memberId, companyId, email, "김서연", "010-2000-0001");

        구성원_토큰 = jwtProvider.issue(memberId, companyId, Role.COMPANY_ADMIN, Instant.now());
    }

    @AfterEach
    void 시드를_지운다() {
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /** 08 §A MeResponse · AU-03 — 조회 키가 요청이 아니라 토큰에서 온다 */
    @Test
    void 구성원_토큰으로_내_정보를_조회할_수_있다() throws Exception {
        // when — memberId 를 어디에도 적지 않고 GET /api/v1/me 를 부르면
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + 구성원_토큰))
                // then — 토큰이 가리키는 구성원의 정보가 08 §A 형태로 나온다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId.toString()))
                .andExpect(jsonPath("$.name").value("김서연"))
                .andExpect(jsonPath("$.phone").value("010-2000-0001"))
                .andExpect(jsonPath("$.role").value("COMPANY_ADMIN"))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.companyName").value("한빛오피스"));
    }

    /** ON-11 · 07 §경로 체계 — 체인을 나누지 않으면 이 토큰으로 관리자 경로가 열린다 */
    @Test
    void 같은_토큰으로_플랫폼_관리자_경로를_열_수_없다() throws Exception {
        mockMvc.perform(get("/admin/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + 구성원_토큰))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_NOT_ACTIVE"));
    }
}
