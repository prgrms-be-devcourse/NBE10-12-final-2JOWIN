package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 딜 삭제 (DL-16·17) — <b>실제 DB로 돌린다</b>.
 *
 * <p>단위 테스트가 못 잡는 것을 본다: <b>소프트 삭제한 딜이 조회에서 실제로 빠지는지</b>다.
 * {@code deleted_at}만 찍고 목록 쿼리가 그 조건을 안 걸면, 지운 딜이 보드에 그대로 남는다 —
 * 목에서는 엔티티 필드만 확인하므로 통과한다.
 *
 * <p>DL-17(견적 연결 시 차단)도 실제 견적 행으로 본다. 판정이
 * {@code QuoteQuery.quoteIdsByDeals} 경계를 지나므로, 그 구현이 회사 스코프를 제대로
 * 거는지까지 함께 드러난다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 커밋된 뒤에 다시 읽어야 삭제를 확인할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DealDeleteIntegrationTest {

    @Autowired
    private DealService dealService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID memberId;
    private UUID customerId;
    private UUID dealId;
    private AccessContext admin;

    @BeforeEach
    void 심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')", companyId, applicationId, "한빛오피스", businessNo);
        jdbc.update("insert into member (id, company_id, email, name, role, status) "
                        + "values (?, ?, ?, ?, 'COMPANY_ADMIN', 'ACTIVE')",
                memberId, companyId, "admin-" + memberId + "@twojo.test", "김서연");
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, memberId, "도담산업");
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version) "
                        + "values (?, ?, ?, ?, ?, 'CONSULT', 0)",
                dealId, companyId, customerId, memberId, "도담 사무가구");

        admin = new AccessContext(companyId, memberId, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    }

    private void 견적(String quoteNo) {
        jdbc.update("insert into quote (id, company_id, deal_id, quote_no, status, vat_mode, "
                        + "supply_amount, vat_amount, total_amount, valid_until, version) "
                        + "values (?, ?, ?, ?, 'DRAFT', 'EXCLUDED', 0, 0, 0, ?, 0)",
                UUID.randomUUID(), companyId, dealId, quoteNo, LocalDate.now().plusDays(30));
    }

    /**
     * <b>이 테스트가 이 PR의 핵심이다.</b> {@code deleted_at}만 찍고 목록이 그 조건을 안 걸면
     * 지운 딜이 보드에 그대로 남는다 — 단위 테스트는 엔티티 필드만 보고 통과한다.
     */
    @Test
    @DisplayName("지운 딜은 목록과 상세에서 빠진다 — 행은 남지만 보이지 않는다")
    void 소프트_삭제가_조회에_반영된다() {
        assertThat(dealService.list(admin, null, null, null, PageRequest.of(0, 20)).content())
                .extracting(item -> item.id()).contains(dealId);

        dealService.delete(admin, dealId, Instant.now());

        assertThat(dealService.list(admin, null, null, null, PageRequest.of(0, 20)).content())
                .extracting(item -> item.id()).doesNotContain(dealId);
        assertThatThrownBy(() -> dealService.get(admin, dealId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        // 하드 삭제가 아니다 — 행은 남고 deleted_at만 찍힌다
        assertThat(jdbc.queryForObject("select count(*) from deal where id = ?", Long.class, dealId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select deleted_at from deal where id = ?", Instant.class, dealId))
                .isNotNull();
    }

    @Test
    @DisplayName("견적이 연결돼 있으면 DEAL_HAS_QUOTES — 경계 조회가 실제로 찾아낸다 (DL-17)")
    void 견적이_있으면_막힌다() {
        견적("Q-9601");

        assertThatThrownBy(() -> dealService.delete(admin, dealId, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEAL_HAS_QUOTES);

        assertThat(jdbc.queryForObject("select deleted_at from deal where id = ?", Instant.class, dealId))
                .isNull();
    }

    /**
     * 상태를 가리지 않는다 — DL-16이 제한하지 않고, 막는 축은 견적 연결 하나뿐이다.
     * 종결 딜에 견적이 없는 경우는 실제로 생긴다(견적 없이 실패 처리한 딜).
     */
    @Test
    @DisplayName("종결(LOST) 딜도 견적이 없으면 지워진다 — 막는 축은 견적뿐이다")
    void 종결_딜도_지워진다() {
        jdbc.update("update deal set stage = 'LOST', lost_from_stage = 'CONSULT', "
                + "lost_reason = '예산 미확보' where id = ?", dealId);

        dealService.delete(admin, dealId, Instant.now());

        assertThat(jdbc.queryForObject("select deleted_at from deal where id = ?", Instant.class, dealId))
                .isNotNull();
    }

    @Test
    @DisplayName("이미 지운 딜을 다시 지우면 404다 — 조회에서 이미 빠져 있다")
    void 두_번_지우면_404() {
        dealService.delete(admin, dealId, Instant.now());

        assertThatThrownBy(() -> dealService.delete(admin, dealId, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
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
}
