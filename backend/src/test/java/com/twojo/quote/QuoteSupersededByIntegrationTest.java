package com.twojo.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.quote.service.QuoteService;
import java.time.LocalDate;
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
 * 대체 견적 이동 (QT-28) — <b>실제 DB로 돌린다</b>.
 *
 * <p>규칙은 셋이 한 판단이다: <b>"대체"는 고객에게 다시 간 것만 뜻한다</b> (#326 확정).
 *
 * <pre>
 * 원본.status ∈ {REJECTED, WITHDRAWN} 이고
 * 복제본.status ≠ DRAFT 인 것 중 sent_at 최대. 없으면 null.
 * </pre>
 *
 * <p><b>목으로는 한 줄도 증명되지 않는다.</b> 값이 파생 쿼리
 * ({@code findFirstByClonedFromQuoteIdAndStatusNotOrderBySentAtDesc})에서 나오고, 그 쿼리의
 * 정렬·제외 조건이 곧 규칙이기 때문이다. 메서드 이름이 한 글자만 어긋나도 Spring Data가 다른
 * 쿼리를 만들어 내는데, 그건 실행해야만 드러난다.
 *
 * <p>화면은 이 값이 있으면 "대체한 견적으로 이동 →"을 그린다
 * ({@code QuoteDetailPage.tsx:123}) — <b>틀린 값은 곧 틀린 링크다.</b>
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 커밋된 뒤에 다시 읽어야 한다
 * ({@code QuoteCloneIntegrationTest}와 같은 이유).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QuoteSupersededByIntegrationTest {

    @Autowired
    private QuoteService quoteService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID dealId;
    private AccessContext ctx;
    private int 채번;

    @BeforeEach
    void 회사와_딜을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        채번 = 0;
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
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, '도담 사무가구', 'NEGOTIATION', 0)",
                dealId, companyId, customerId, memberId);

        ctx = new AccessContext(companyId, memberId, Role.SALES_REP, AccessScope.OWNED_ONLY);
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from quote where company_id = ?", companyId);
        jdbc.update("delete from deal where id = ?", dealId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where id = ?", memberId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    /**
     * {@code sentAt}은 KST 문자열이거나 {@code null}(DRAFT)이다 — 정렬 축이라 직접 넣어야 한다.
     * {@code clonedFrom}이 {@code null}이면 원본이다.
     */
    private UUID 견적(String status, UUID clonedFrom, String sentAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, "
                        + "cloned_from_quote_id, sent_at, version) "
                        + "values (?, ?, ?, ?, ?, 'EXCLUDED', 100000, 10000, 110000, ?, ?, ?::timestamptz, 0)",
                id, companyId, dealId, "Q-93" + (채번++) + "-" + id.toString().substring(0, 4), status,
                LocalDate.now().plusDays(30), clonedFrom, sentAt);
        return id;
    }

    private UUID 대체견적(UUID quoteId) {
        return quoteService.get(ctx, quoteId).supersededByQuoteId();
    }

    /**
     * <b>이 테스트가 이 이슈의 핵심이다.</b> 지금까지 이 값은 하드코딩 {@code null}이었고,
     * 화면은 이미 링크를 그리고 있었다 — 목에서는 보이고 실 API에서는 안 보였다 (#129 잔여 갭).
     */
    @Test
    @DisplayName("반려된 견적은 발송된 복제본을 가리킨다 (QT-28)")
    void 반려_원본이_대체본을_가리킨다() {
        UUID 원본 = 견적("REJECTED", null, "2026-08-19 10:00:00+09");
        UUID 대체본 = 견적("SENT", 원본, "2026-08-26 11:00:00+09");

        assertThat(대체견적(원본)).isEqualTo(대체본);
    }

    /** 회수(QT-17)도 같은 자리다 — QT-28이 "반려·회수된"으로 둘을 함께 적는다 */
    @Test
    @DisplayName("회수된 견적도 발송된 복제본을 가리킨다")
    void 회수_원본도_같다() {
        UUID 원본 = 견적("WITHDRAWN", null, "2026-08-20 10:00:00+09");
        UUID 대체본 = 견적("VIEWED", 원본, "2026-08-24 10:00:00+09");

        assertThat(대체견적(원본)).isEqualTo(대체본);
    }

    /**
     * <b>DRAFT는 대체가 아니다.</b> 아직 고객에게 가지 않았으니 "대체한" 것이 아니라 대체할
     * 예정이다. 이 초안이 끝내 발송되지 않으면 화면이 계속 거짓말을 하고, 되돌릴 방법이 없다.
     */
    @Test
    @DisplayName("복제본이 작성 중이면 대체로 보지 않는다")
    void DRAFT_복제본은_제외된다() {
        UUID 원본 = 견적("REJECTED", null, "2026-08-19 10:00:00+09");
        견적("DRAFT", 원본, null);

        assertThat(대체견적(원본)).isNull();
    }

    /**
     * 복제는 상태와 무관하지만(QT-19) QT-28은 "<b>반려·회수된</b> 견적에서"라 적는다.
     * 이 조건이 없으면 진행 중인 견적에도 "대체한 견적으로 이동"이 뜬다 — 대체될 이유가 없다.
     */
    @Test
    @DisplayName("진행 중인 견적은 복제본이 있어도 대체를 가리키지 않는다")
    void 원본이_진행_중이면_null이다() {
        UUID 원본 = 견적("SENT", null, "2026-08-19 10:00:00+09");
        견적("SENT", 원본, "2026-08-26 10:00:00+09");

        assertThat(대체견적(원본)).isNull();
    }

    /** 기간 만료는 시스템 전이라 "다시 제안했다"는 뜻이 아니다 — 반려·회수와 갈린다 */
    @Test
    @DisplayName("기간 만료된 견적도 대체를 가리키지 않는다")
    void 만료_원본도_null이다() {
        UUID 원본 = 견적("EXPIRED", null, "2026-08-19 10:00:00+09");
        견적("SENT", 원본, "2026-08-26 10:00:00+09");

        assertThat(대체견적(원본)).isNull();
    }

    /**
     * <b>여러 번 복제한 경우가 이 링크가 가장 필요한 경우다.</b> 애매하다고 {@code null}을 주면
     * 제일 복잡한 딜에서 화면이 빈다. 기준은 <b>마지막으로 발송된</b> 것이다 —
     * {@code created_at}이 아닌 이유는 먼저 만든 복제본을 나중에 보낼 수 있어서다.
     */
    @Test
    @DisplayName("복제본이 여럿이면 가장 최근에 발송된 것을 가리킨다")
    void 여러_복제본_중_최근_발송() {
        UUID 원본 = 견적("REJECTED", null, "2026-08-19 10:00:00+09");
        견적("WITHDRAWN", 원본, "2026-08-21 10:00:00+09");
        UUID 최근발송 = 견적("SENT", 원본, "2026-08-27 10:00:00+09");
        견적("DRAFT", 원본, null);   // 아직 안 보낸 것은 최신이어도 제외다

        assertThat(대체견적(원본)).isEqualTo(최근발송);
    }

    /** 복제본이 없으면 null이다 — 반려됐다고 항상 대체본이 있는 것은 아니다 */
    @Test
    @DisplayName("복제본이 없으면 null이다")
    void 복제본이_없으면_null() {
        UUID 원본 = 견적("REJECTED", null, "2026-08-19 10:00:00+09");

        assertThat(대체견적(원본)).isNull();
    }
}
