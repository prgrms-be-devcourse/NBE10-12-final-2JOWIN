package com.twojo.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.product.dto.CreateProductRequest;
import com.twojo.product.repository.ProductRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 사전 검사를 통과한 요청이 DB {@code uk_product_company_name}에 걸릴 때 409가 나는지 검증한다.
 *
 * <p><b>목으로는 성립하지 않는다.</b> {@code save()}는 INSERT를 커밋까지 미루므로 예외가
 * 서비스의 try 블록 밖에서 터져 500이 된다. {@code saveAndFlush()}를 {@code save()}로
 * 되돌리면 {@code ProductServiceTest}는 그대로 통과하고 이 테스트만 빨간불이 된다.
 *
 * <p>동시 요청을 스레드로 재현하는 대신 사전 검사만 거짓으로 만든다 — "사전 검사는 통과,
 * DB는 거부"라는 같은 상태를 결정적으로 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductServiceIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ProductRepository productRepository;

    private final UUID applicationId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final String productName = "복사용지-" + UUID.randomUUID();

    private AccessContext admin;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO application (id, company_name, business_no, email, status) "
                        + "VALUES (?, 'it-co', ?, ?, 'APPROVED')",
                applicationId, unique, unique + "@example.com");
        jdbc.update("INSERT INTO company (id, application_id, name, business_no, status) "
                        + "VALUES (?, ?, 'it-co', ?, 'ACTIVE')",
                companyId, applicationId, unique);

        admin = new AccessContext(companyId, UUID.randomUUID(), Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM product WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM company WHERE id = ?", companyId);
        jdbc.update("DELETE FROM application WHERE id = ?", applicationId);
    }

    @Test
    @DisplayName("사전 검사를 통과해도 DB UNIQUE에 걸리면 409다 — 500이 아니다")
    void create_uniqueViolation_conflict() {
        productService.create(admin, new CreateProductRequest(productName, "박스", 25_000L, null));

        // 동시 요청으로 사전 검사가 빈 결과를 본 상태를 만든다
        given(productRepository.existsByCompanyIdAndName(companyId, productName)).willReturn(false);

        assertThatThrownBy(() ->
                productService.create(admin, new CreateProductRequest(productName, "박스", 30_000L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NAME_DUPLICATED);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM product WHERE company_id = ? AND name = ?",
                Integer.class, companyId, productName))
                .isEqualTo(1);
    }
}
