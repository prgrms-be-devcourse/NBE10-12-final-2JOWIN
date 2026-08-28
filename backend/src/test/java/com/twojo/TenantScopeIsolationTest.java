package com.twojo;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.customer.repository.CustomerContactRepository;
import com.twojo.customer.repository.CustomerRepository;
import com.twojo.product.repository.ProductRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회사 스코프가 실제 SQL로 걸리는지 검증한다 (SC-01).
 *
 * <p>단위 테스트는 리포지토리를 목으로 대체하므로 WHERE 절이 맞는지 증명하지 못한다.
 * 테넌트 격리는 조용히 뚫려도 티가 안 나는 종류라, 실제 PostgreSQL에 두 회사의 행을 넣고
 * 조회 결과가 갈리는지 확인한다. (로컬은 {@code docker compose -f infra/docker-compose.yml up -d} 선행)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantScopeIsolationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerContactRepository customerContactRepository;
    @Autowired private ProductRepository productRepository;

    private final UUID companyA = UUID.randomUUID();
    private final UUID companyB = UUID.randomUUID();
    private final UUID memberA = UUID.randomUUID();
    private final UUID memberB = UUID.randomUUID();
    private final UUID customerA = UUID.randomUUID();
    private final UUID customerB = UUID.randomUUID();
    private final UUID deletedCustomerA = UUID.randomUUID();
    private final UUID contactA = UUID.randomUUID();
    private final UUID productA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        company(companyA, memberA, "한빛오피스", "111-11-11111", "a");
        company(companyB, memberB, "다른회사", "222-22-22222", "b");

        customer(customerA, companyA, memberA, "도담건설", null);
        customer(deletedCustomerA, companyA, memberA, "폐업고객사", "now()");
        customer(customerB, companyB, memberB, "타사고객사", null);

        jdbc.update("""
                INSERT INTO customer_contact (id, customer_id, name, title, email, is_primary)
                VALUES (?, ?, '이수정', '총무팀 대리', 'sujeong@dodam.co.kr', true)
                """, contactA, customerA);

        jdbc.update("""
                INSERT INTO product (id, company_id, name, unit, unit_price, status)
                VALUES (?, ?, '1200 사무책상', '개', 180000, 'ACTIVE')
                """, productA, companyA);
    }

    @Test
    @DisplayName("자기 회사 고객사는 조회된다")
    void ownCompanyCustomer_found() {
        assertThat(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(customerA, companyA))
                .isPresent();
    }

    @Test
    @DisplayName("다른 회사 고객사는 id를 알아도 조회되지 않는다 (SC-01)")
    void otherCompanyCustomer_notFound() {
        assertThat(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(customerB, companyA))
                .isEmpty();
    }

    @Test
    @DisplayName("소프트 삭제된 고객사는 조회되지 않는다")
    void softDeletedCustomer_notFound() {
        assertThat(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(deletedCustomerA, companyA))
                .isEmpty();
    }

    @Test
    @DisplayName("다른 회사 상품은 조회되지 않는다 (SC-01)")
    void otherCompanyProduct_notFound() {
        assertThat(productRepository.findByIdAndCompanyId(productA, companyB)).isEmpty();
    }

    @Test
    @DisplayName("담당자는 소속 고객사로만 확인된다 — 다른 고객사로 물으면 false")
    void contactBelongsToOwningCustomerOnly() {
        assertThat(customerContactRepository.existsByIdAndCustomerId(contactA, customerA)).isTrue();
        assertThat(customerContactRepository.existsByIdAndCustomerId(contactA, customerB)).isFalse();
    }

    private void company(UUID companyId, UUID memberId, String name, String businessNo, String emailPrefix) {
        UUID applicationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO application (id, company_name, business_no, email, status)
                VALUES (?, ?, ?, ?, 'APPROVED')
                """, applicationId, name, businessNo, emailPrefix + "@example.com");
        jdbc.update("""
                INSERT INTO company (id, application_id, name, business_no, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, companyId, applicationId, name, businessNo);
        jdbc.update("""
                INSERT INTO member (id, company_id, email, name, role, status)
                VALUES (?, ?, ?, '담당자', 'COMPANY_ADMIN', 'ACTIVE')
                """, memberId, companyId, emailPrefix + "-member@example.com");
    }

    private void customer(UUID customerId, UUID companyId, UUID memberId, String name, String deletedAt) {
        jdbc.update("""
                INSERT INTO customer (id, company_id, created_by_member_id, name, deleted_at)
                VALUES (?, ?, ?, ?, %s)
                """.formatted(deletedAt == null ? "NULL" : deletedAt),
                customerId, companyId, memberId, name);
    }
}
