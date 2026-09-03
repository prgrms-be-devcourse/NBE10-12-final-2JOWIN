package com.twojo.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.product.dto.CreateProductRequest;
import com.twojo.product.dto.ProductResponse;
import com.twojo.product.dto.UpdateProductRequest;
import com.twojo.product.entity.Product;
import com.twojo.product.repository.ProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 상품 서비스 — 역할 판정(PR-09) · 이름 중복(PR-02) · 회사 스코프(SC-01) · PATCH 부분 수정(08 §B).
 *
 * <p>역할 위반은 403, 리소스 범위 위반은 404다 (Q-43).
 * DB 제약이 개입하는 경로는 목으로 재현되지 않아 {@link ProductServiceIntegrationTest}가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private static final AccessContext ADMIN =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private ProductRepository productRepository;
    @InjectMocks private ProductService productService;

    private static CreateProductRequest 등록요청() {
        return new CreateProductRequest("A4 복사용지", "박스", 25_000L, "80g 2500매");
    }

    private static Product 상품() {
        return Product.create(COMPANY_ID, "A4 복사용지", "박스", 25_000L, "80g 2500매");
    }

    @Test
    @DisplayName("영업 담당자는 상품을 등록할 수 없다 — 403 FORBIDDEN (PR-09, Q-43)")
    void create_salesRep_forbidden() {
        assertThatThrownBy(() -> productService.create(SALES, 등록요청()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        then(productRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("영업 담당자는 판매 중지도 할 수 없다 — 조회보다 역할 검사가 먼저다")
    void discontinue_salesRep_forbidden() {
        assertThatThrownBy(() -> productService.discontinue(SALES, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        then(productRepository).should(never()).findByIdAndCompanyId(any(), any());
    }

    @Test
    @DisplayName("이름이 겹치면 409 — 판매 중지된 상품 이름도 포함한다 (PR-02)")
    void create_duplicateName_conflict() {
        given(productRepository.existsByCompanyIdAndName(COMPANY_ID, "A4 복사용지")).willReturn(true);

        assertThatThrownBy(() -> productService.create(ADMIN, 등록요청()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NAME_DUPLICATED);

        then(productRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("타사 상품을 수정하려 하면 404 — 403이 아니다 (SC-01·09)")
    void update_otherCompany_notFound() {
        given(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).willReturn(Optional.empty());

        UpdateProductRequest request = new UpdateProductRequest("새 이름", "박스", 30_000L, null);

        assertThatThrownBy(() -> productService.update(ADMIN, PRODUCT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("단가만 보내면 이름·단위·설명은 그대로다 — 중복 검사도 돌지 않는다 (08 §B)")
    void update_onlyUnitPrice_keepsOtherFields() {
        Product product = 상품();
        given(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).willReturn(Optional.of(product));
        given(productRepository.saveAndFlush(product)).willReturn(product);

        ProductResponse response =
                productService.update(ADMIN, PRODUCT_ID, new UpdateProductRequest(null, null, 31_000L, null));

        assertThat(response.unitPrice()).isEqualTo(31_000L);
        assertThat(response.name()).isEqualTo("A4 복사용지");
        assertThat(response.unit()).isEqualTo("박스");
        assertThat(response.description()).isEqualTo("80g 2500매");

        then(productRepository).should(never()).existsByCompanyIdAndNameAndIdNot(any(), any(), any());
    }

    @Test
    @DisplayName("판매 중지는 상태만 바꾼다 — 관리자면 통과")
    void discontinue_admin_changesStatus() {
        Product product = 상품();
        given(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).willReturn(Optional.of(product));

        assertThat(productService.discontinue(ADMIN, PRODUCT_ID).status())
                .isEqualTo(Product.Status.DISCONTINUED.name());
    }
}
