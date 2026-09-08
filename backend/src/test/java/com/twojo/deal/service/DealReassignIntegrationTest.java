package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.DealCommand;
import com.twojo.boundary.DealQuery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link DealCommand#reassignOpenDeals}의 <b>엔티티 경유 규약</b>을 실제 DB로 고정한다 (MB-14, Q-48).
 *
 * <p><b>목으로는 성립하지 않는다.</b> 이 구현이 JPQL 일괄 update 대신 엔티티를 한 건씩 바꾸는 이유는
 * flush 때 {@code @Version}과 {@code updated_at}이 올라가 열어 둔 딜 상세의 낙관적 락(DL-05)이
 * 이관을 알아채게 하려는 것이다. 그 효과는 실제 UPDATE가 나가야 보인다 —
 * {@code DealCommandImplTest}는 "무엇을 옮기고 무엇을 돌려주는가"만 본다.
 *
 * <p>함께 고정하는 것: 세는 집합({@link DealQuery#countOpenAssigned})과 옮기는 집합이 같다 —
 * 종결(WON)·소프트 삭제·타 구성원 담당은 둘 다에서 빠진다. #134 리뷰에서 C가 찾은 갭이 그 불일치였다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 붙이면 이관이 테스트 트랜잭션에 합류해 flush·커밋이
 * 미뤄지고 version 증가를 DB에서 읽을 수 없다. {@code DealStageConcurrencyTest}와 같은 이유다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DealReassignIntegrationTest {

    @Autowired
    private DealCommand dealCommand;
    @Autowired
    private DealQuery dealQuery;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID applicationId;
    private UUID companyId;
    private UUID fromMemberId;
    private UUID toMemberId;
    private UUID otherMemberId;
    private UUID customerId;
    private UUID leadId;
    private UUID negotiationId;
    private UUID wonId;
    private UUID deletedId;
    private UUID othersId;

    @BeforeEach
    void 담당_Deal_다섯_건을_심는다() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        fromMemberId = UUID.randomUUID();
        toMemberId = UUID.randomUUID();
        otherMemberId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        String businessNo = applicationId.toString().substring(0, 13);

        jdbc.update("insert into application (id, company_name, business_no, email, applicant_name, status) "
                        + "values (?, ?, ?, ?, '김서연', 'APPROVED')",
                applicationId, "한빛오피스", businessNo, "admin-" + applicationId + "@twojo.test");
        jdbc.update("insert into company (id, application_id, name, business_no, status) "
                        + "values (?, ?, ?, ?, 'ACTIVE')",
                companyId, applicationId, "한빛오피스", businessNo);
        for (UUID memberId : List.of(fromMemberId, toMemberId, otherMemberId)) {
            jdbc.update("insert into member (id, company_id, email, name, role, status) "
                            + "values (?, ?, ?, ?, 'SALES_REP', 'ACTIVE')",
                    memberId, companyId, "sales-" + memberId + "@twojo.test", "박지훈");
        }
        jdbc.update("insert into customer (id, company_id, created_by_member_id, name) values (?, ?, ?, ?)",
                customerId, companyId, fromMemberId, "도담산업");

        leadId = insertDeal(fromMemberId, "LEAD", false);
        negotiationId = insertDeal(fromMemberId, "NEGOTIATION", false);
        wonId = insertDeal(fromMemberId, "WON", false);
        deletedId = insertDeal(fromMemberId, "LEAD", true);
        othersId = insertDeal(otherMemberId, "QUOTE", false);
    }

    /** version 0 · updated_at은 하루 전으로 — 이관이 둘을 올리는지 시각 정밀도와 무관하게 보기 위해 */
    private UUID insertDeal(UUID assigneeId, String stage, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into deal (id, company_id, customer_id, assignee_member_id, title, stage, version, "
                        + "deleted_at, updated_at) values (?, ?, ?, ?, ?, ?, 0, ?, now() - interval '1 day')",
                id, companyId, customerId, assigneeId, "도담 " + stage, stage,
                deleted ? OffsetDateTime.now() : null);
        return id;
    }

    @AfterEach
    void 지운다() {
        jdbc.update("delete from deal where company_id = ?", companyId);
        jdbc.update("delete from customer where id = ?", customerId);
        jdbc.update("delete from member where company_id = ?", companyId);
        jdbc.update("delete from company where id = ?", companyId);
        jdbc.update("delete from application where id = ?", applicationId);
    }

    @Test
    @DisplayName("진행 중 담당 Deal만 옮기고 version·updated_at이 오른다 — 종결·삭제·타인 담당은 그대로")
    void 이관은_엔티티를_경유한다() {
        long before = dealQuery.countOpenAssigned(companyId, fromMemberId);
        assertThat(before).isEqualTo(2);   // 세는 집합: LEAD·NEGOTIATION

        List<UUID> moved = dealCommand.reassignOpenDeals(companyId, fromMemberId, toMemberId);

        // 옮기는 집합 == 세는 집합 — A는 사전 판정 건수와 반환 건수가 같다고 단언해도 된다
        assertThat(moved).containsExactlyInAnyOrder(leadId, negotiationId);
        assertThat(dealQuery.countOpenAssigned(companyId, fromMemberId)).isZero();
        assertThat(dealQuery.countOpenAssigned(companyId, toMemberId)).isEqualTo(before);

        // 엔티티 경유의 효과 — @Version이 오르고 updated_at이 갱신된다 (DL-05가 이관을 알아채는 근거)
        for (UUID id : moved) {
            assertThat(assigneeOf(id)).isEqualTo(toMemberId);
            assertThat(versionOf(id)).isOne();
            assertThat(updatedRecently(id)).isTrue();
        }

        // 남는 것 — 종결(Q-48)·소프트 삭제·타 구성원 담당은 담당자도 version도 그대로
        for (UUID id : List.of(wonId, deletedId)) {
            assertThat(assigneeOf(id)).isEqualTo(fromMemberId);
            assertThat(versionOf(id)).isZero();
            assertThat(updatedRecently(id)).isFalse();
        }
        assertThat(assigneeOf(othersId)).isEqualTo(otherMemberId);
        assertThat(versionOf(othersId)).isZero();
    }

    @Test
    @DisplayName("옮길 Deal이 없으면 빈 목록 — 종결 Deal만 남은 구성원도 이관 대상 없이 비활성화된다 (Q-48)")
    void 종결만_남은_구성원은_0건() {
        jdbc.update("update deal set stage = 'LOST', lost_from_stage = 'LEAD' where id in (?, ?)",
                leadId, negotiationId);

        assertThat(dealQuery.countOpenAssigned(companyId, fromMemberId)).isZero();
        assertThat(dealCommand.reassignOpenDeals(companyId, fromMemberId, toMemberId)).isEmpty();
        assertThat(versionOf(leadId)).isZero();
    }

    private UUID assigneeOf(UUID dealId) {
        return jdbc.queryForObject("select assignee_member_id from deal where id = ?", UUID.class, dealId);
    }

    private int versionOf(UUID dealId) {
        return jdbc.queryForObject("select version from deal where id = ?", Integer.class, dealId);
    }

    private boolean updatedRecently(UUID dealId) {
        return jdbc.queryForObject("select updated_at > now() - interval '1 hour' from deal where id = ?",
                Boolean.class, dealId);
    }
}
