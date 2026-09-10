package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 * <p>{@code @Transactional}을 <b>클래스에 붙이지 않는다</b> — 붙이면 이관이 테스트 트랜잭션에 합류해
 * flush·커밋이 미뤄지고 version 증가를 DB에서 읽을 수 없다. {@code DealStageConcurrencyTest}와 같은 이유다.
 *
 * <p>대신 이관 호출만 {@link TransactionTemplate}로 감싼다 — 계약이 {@code MANDATORY}라
 * 트랜잭션 없이 부르면 거부된다 (#227). <b>이 편이 실제와 같다</b>: 운영에서 이 메서드는 항상
 * {@code MemberAdminService.deactivate}의 쓰기 트랜잭션 안에서 불린다. 예전에는 기본
 * {@code REQUIRED}라 독립 호출이 되었을 뿐이고, 그 경로는 어디에도 없었다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DealReassignIntegrationTest {

    @Autowired
    private DealCommand dealCommand;
    @Autowired
    private DealQuery dealQuery;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 운영의 호출자(비활성화)가 여는 것과 같은 쓰기 트랜잭션 — 커밋까지 끝내고 나온다 */
    private List<UUID> 트랜잭션_안에서_이관() {
        return new TransactionTemplate(transactionManager)
                .execute(status -> dealCommand.reassignOpenDeals(companyId, fromMemberId, toMemberId));
    }
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

        List<UUID> moved = 트랜잭션_안에서_이관();

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
        assertThat(트랜잭션_안에서_이관()).isEmpty();
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

    /**
     * <b>계약의 약속을 실제로 지키는지 본다</b> (#227). javadoc이 "호출자의 쓰기 트랜잭션이 필수"라고
     * 하는데 기본 {@code REQUIRED}면 트랜잭션 없이 불러도 <b>조용히 자기 것을 열고 커밋한다</b> —
     * 그러면 "비활성화가 롤백되면 이관도 되돌아간다"는 약속이 그 경로에서 깨지고 흔적도 없다.
     *
     * <p>{@code MANDATORY}로 올려 그 자리에서 실패하게 했다. 이 단언이 없으면 누군가
     * {@code @Transactional} 기본값으로 되돌려도 아무도 모른다.
     */
    @Test
    @DisplayName("트랜잭션 없이 부르면 거부한다 — 조용히 자기 트랜잭션을 열지 않는다 (#227)")
    void 트랜잭션_없는_호출은_거부() {
        assertThatThrownBy(() -> dealCommand.reassignOpenDeals(companyId, fromMemberId, toMemberId))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(dealQuery.countOpenAssigned(companyId, fromMemberId)).isEqualTo(2);   // 아무것도 안 옮겼다
        assertThat(versionOf(leadId)).isZero();
    }
}
