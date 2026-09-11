package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.SalesStatsQuery;
import com.twojo.boundary.SalesStatsQuery.StageConversion;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전환율(DB-07)이 <b>실제 DB에서 도는지</b>를 본다 (#307).
 *
 * <p>{@code SalesStatsQueryImplTest}는 도달 계산의 산수를 본다 — 리포지토리와 감사 창구가 목이라
 * <b>JPQL도 payload도 한 번 읽지 않는다.</b> 여기서 보는 것은 목이 절대 드러내지 못하는 셋이다:
 * 등록일 코호트가 한국 날짜 경계로 끊기는가, 소프트 삭제·타사 딜이 빠지는가, 그리고
 * {@code audit_log.payload}의 <b>실제 모양</b>({@code changes.stage.before/after})을 읽어내는가.
 *
 * <p>payload는 특히 목으로 못 잡는다 — B의 리스너가 쓰는 모양과 {@code AuditQueryImpl}이 읽는
 * 경로가 어긋나면 전이가 조용히 0건이 되고, 되돌린 딜의 보정만 사라져 수치가 <b>그럴듯하게</b> 틀린다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다</b> — 뒷정리를 직접 한다
 * ({@code SalesStatsIntegrationTest}와 같은 이유).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SalesStatsConversionIntegrationTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Autowired
    private SalesStatsQuery salesStatsQuery;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID 타사company;
    private UUID 타사application;

    @BeforeEach
    void 회사를_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')", companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, '박지훈', 'SALES_REP', 'ACTIVE')",
                memberId, companyId, "sales-" + memberId + "@twojo.test");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "도담산업");
    }

    /**
     * 다른 회사의 딜 하나 + 그 전이 이력. 성사(WON)까지 보낸다 — 분모에 섞이면 모든 쌍이 흔들린다.
     *
     * @return 심은 딜 id
     */
    private UUID 타사_WON_딜을_심는다() {
        타사application = UUID.randomUUID();
        타사company = UUID.randomUUID();
        UUID 타사member = UUID.randomUUID();
        UUID 타사customer = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        String businessNo = 타사application.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '최민호', 'APPROVED')",
                타사application, "성원물산", businessNo, "admin-" + 타사application + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')", 타사company, 타사application, "성원물산", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, '최민호', 'SALES_REP', 'ACTIVE')",
                타사member, 타사company, "sales-" + 타사member + "@twojo.test");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                타사customer, 타사company, 타사member, "성원산업");
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, "
                        + "created_at, version) values (?, ?, ?, ?, '타사 딜', 'WON', ?::timestamptz, 0)",
                dealId, 타사company, 타사customer, 타사member, "2026-09-10 10:00:00+09");
        jdbc.update("insert into audit_log (id, company_id, entity_type, entity_id, event_type, "
                        + "actor_type, actor_id, occurred_at, payload) "
                        + "values (?, ?, 'DEAL', ?, 'STAGE_MOVED', 'SYSTEM', null, ?::timestamptz, cast(? as jsonb))",
                UUID.randomUUID(), 타사company, dealId, "2026-09-11 10:00:00+09",
                "{\"dealId\":\"" + dealId + "\",\"changes\":{\"stage\":{\"before\":\"LEAD\",\"after\":\"WON\"}}}");
        return dealId;
    }

    @AfterEach
    void 지운다() {
        if (타사company != null) {
            jdbc.update("delete from audit_log where company_id = ?", 타사company);
            jdbc.update("delete from deal where company_id = ?", 타사company);
            jdbc.update("delete from customer where company_id = ?", 타사company);
            jdbc.update("delete from member where company_id = ?", 타사company);
            jdbc.update("delete from company where id = ?", 타사company);
            jdbc.update("delete from application where id = ?", 타사application);
            타사company = null;
        }
        jdbc.update("delete from audit_log where company_id = ?", companyId);
        jdbc.update("delete from deal where company_id = ?", companyId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where company_id = ?", companyId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /** {@code registeredAt}은 KST 문자열이다 — 코호트 경계가 한국 날짜로 끊기는지 보려면 직접 넣어야 한다 */
    private UUID 딜(String stage, String lostFrom, String registeredAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, "
                        + "lost_from_stage, created_at, version) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?::timestamptz, 0)",
                id, companyId, customerId, memberId, "딜-" + stage, stage, lostFrom, registeredAt);
        return id;
    }

    private UUID 딜(String stage, String registeredAt) {
        return 딜(stage, null, registeredAt);
    }

    /** B의 리스너가 쓰는 payload 모양 그대로 — {@code AuditLogListener.on(DealStageChanged)} */
    private void 전이이력(UUID dealId, String before, String after, String occurredAt) {
        jdbc.update("insert into audit_log (id, company_id, entity_type, entity_id, event_type, "
                        + "actor_type, actor_id, occurred_at, payload) "
                        + "values (?, ?, 'DEAL', ?, 'STAGE_MOVED', 'MEMBER', ?, ?::timestamptz, cast(? as jsonb))",
                UUID.randomUUID(), companyId, dealId, memberId, occurredAt,
                "{\"dealId\":\"" + dealId + "\",\"changes\":{\"stage\":{\"before\":\"" + before
                        + "\",\"after\":\"" + after + "\"}}}");
    }

    private double rateOf(List<StageConversion> rows, String from) {
        return rows.stream().filter(r -> r.fromStage().equals(from)).findFirst().orElseThrow().rate();
    }

    private List<StageConversion> 전환율() {
        return salesStatsQuery.conversions(companyId, FROM, TO);
    }

    /**
     * <b>이 테스트가 실패하면 되돌린 딜의 봉우리가 조용히 사라진다.</b> 리스너가 쓰는 payload와
     * {@code AuditQueryImpl}이 읽는 경로({@code changes.stage.after})가 어긋나도 예외는 없고
     * 그 행만 버려진다 — 수치가 틀렸다는 신호가 어디에도 안 뜬다.
     */
    @Test
    @DisplayName("되돌린 딜의 봉우리를 실제 payload에서 읽어낸다 (DL-08)")
    void 이력_보정이_실_DB에서_돈다() {
        UUID 되돌아온딜 = 딜("CONSULT", "2026-09-10 10:00:00+09");
        전이이력(되돌아온딜, "CONSULT", "QUOTE", "2026-09-11 10:00:00+09");
        전이이력(되돌아온딜, "QUOTE", "CONSULT", "2026-09-12 10:00:00+09");

        assertThat(rateOf(전환율(), "CONSULT")).isEqualTo(1.0d);   // 현재는 상담, 도달은 견적
    }

    /**
     * 등록일 경계는 <b>한국 날짜</b>다 ({@code DealPeriod}). 서버 시간대로 끊으면 자정 부근에
     * 등록된 딜이 옆 코호트로 새고, 월별로 이어 붙일 때 한 딜이 두 달에 걸치거나 사라진다.
     */
    @Test
    @DisplayName("등록일 코호트는 한국 날짜로 끊긴다 — 양 끝 날짜를 포함한다")
    void 코호트_경계는_KST다() {
        딜("CONSULT", "2026-08-31 23:59:59+09");   // 기간 직전 — 빠진다
        딜("LEAD", "2026-09-01 00:00:00+09");      // 하한 당일 0시 — 들어온다
        딜("CONSULT", "2026-09-30 23:59:59+09");   // 상한 당일 끝 — 들어온다
        딜("LEAD", "2026-10-01 00:00:00+09");      // 기간 직후 — 빠진다

        // 코호트는 리드 하나·상담 하나라 0.5다. 네 경계가 각각 다른 값을 만든다:
        //   하한이 밀려 8/31이 섞이면 2/3 · 상한이 넘쳐 10/1이 섞이면 1/3
        //   상한이 그날 0시로 짧아져 9/30이 빠지면 0 — 가장 위험한 방향이다
        assertThat(rateOf(전환율(), "LEAD")).isEqualTo(0.5d);
    }

    /**
     * 소프트 삭제된 딜은 모집단에서 빠진다 (SC-01, docs/11 §1.5). 분모에 남으면 지운 딜이
     * 전환율을 계속 끌어내린다.
     */
    @Test
    @DisplayName("소프트 삭제된 딜은 모집단에서 빠진다")
    void 삭제된_딜은_제외된다() {
        딜("CONSULT", "2026-09-10 10:00:00+09");
        UUID 지운딜 = 딜("LEAD", "2026-09-10 10:00:00+09");
        jdbc.update("update deal set deleted_at = now() where id = ?", 지운딜);

        assertThat(rateOf(전환율(), "LEAD")).isEqualTo(1.0d);   // 남은 한 건은 상담 도달
    }

    /**
     * 실패 딜은 {@code lost_from_stage}로 되짚는다 — 이 컬럼이 실제로 읽히는지는 목이 못 본다.
     * 재개(DL-12) 시 null로 비워지므로 비어 있는 LOST 행이 존재할 수는 없지만, 그때도
     * 딜이 분모에서 빠지지는 않는다.
     */
    @Test
    @DisplayName("실패 딜은 lost_from_stage로 되짚는다 (DL-10)")
    void 실패_딜의_도달_지점() {
        딜("LOST", "QUOTE", "2026-09-10 10:00:00+09");
        딜("LOST", "LEAD", "2026-09-10 10:00:00+09");

        List<StageConversion> rows = 전환율();

        assertThat(rateOf(rows, "LEAD")).isEqualTo(0.5d);
        assertThat(rateOf(rows, "CONSULT")).isEqualTo(1.0d);
    }

    /**
     * 회사 스코프 (SC-01) — 모집단 쿼리와 감사 창구 <b>둘 다</b> 회사로 끊는지 본다.
     *
     * <p>한쪽만 새도 조용히 틀린다. 모집단이 새면 타사 딜이 분모에 들어가 전환율이 내려가고,
     * 감사 쪽이 새도 {@code computeIfPresent}가 걸러 티가 안 난다 — 그래서 <b>타사 딜을
     * 멀리 보낸 상태로</b> 심는다. 분모에 섞이면 값이 0.5에서 흔들린다.
     */
    @Test
    @DisplayName("타사 딜은 분모에도 이력에도 들어오지 않는다 (SC-01)")
    void 타사_딜은_새지_않는다() {
        딜("LEAD", "2026-09-10 10:00:00+09");
        딜("CONSULT", "2026-09-10 10:00:00+09");
        UUID 타사딜 = 타사_WON_딜을_심는다();

        List<StageConversion> rows = 전환율();

        assertThat(rateOf(rows, "LEAD")).isEqualTo(0.5d);   // 타사 WON 딜이 섞이면 0.667이 된다
        assertThat(rateOf(rows, "NEGOTIATION")).isZero();   // 성사 도달은 우리 회사에 없다
        jdbc.update("delete from audit_log where entity_id = ?", 타사딜);
    }

    /** 딜이 없는 기간을 물어도 네 칸이 0으로 선다 — 초기 상태가 곧 정상 상태다 (완료 조건 4) */
    @Test
    @DisplayName("딜이 없는 기간도 네 칸이 0으로 온다")
    void 빈_기간() {
        List<StageConversion> rows = 전환율();

        assertThat(rows).hasSize(4).allSatisfy(r -> assertThat(r.rate()).isZero());
        assertThat(rows).extracting(StageConversion::fromStage)
                .containsExactly("LEAD", "CONSULT", "QUOTE", "NEGOTIATION");
    }
}
