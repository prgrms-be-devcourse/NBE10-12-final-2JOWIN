package com.twojo.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
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

@ExtendWith(MockitoExtension.class)
class ProductQueryImplTest {

    private static final AccessContext CTX =
            new AccessContext(UUID.randomUUID(), UUID.randomUUID(), Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private ProductRepository productRepository;
    @InjectMocks private ProductQueryImpl productQuery;

    @Test
    @DisplayName("견적 항목에 복사될 이름·단위·단가를 돌려준다 (QT-24)")
    void get_returnsSnapshot() {
        UUID productId = UUID.randomUUID();
        Product product = org.mockito.Mockito.mock(Product.class);
        given(product.getId()).willReturn(productId);
        given(product.getName()).willReturn("1200 사무책상");
        given(product.getUnit()).willReturn("개");
        given(product.getUnitPrice()).willReturn(180_000L);
        given(productRepository.findByIdAndCompanyId(productId, CTX.companyId()))
                .willReturn(Optional.of(product));

        var snapshot = productQuery.get(CTX, productId);

        assertThat(snapshot.name()).isEqualTo("1200 사무책상");
        assertThat(snapshot.unit()).isEqualTo("개");
        assertThat(snapshot.unitPrice()).isEqualTo(180_000L);
    }

    @Test
    @DisplayName("범위 밖 상품은 404 (SC-09)")
    void get_outOfScope_throwsNotFound() {
        UUID productId = UUID.randomUUID();
        given(productRepository.findByIdAndCompanyId(productId, CTX.companyId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> productQuery.get(CTX, productId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("판매 중인 상품은 견적에 추가할 수 있다 (PR-06)")
    void isSellable_active_true() {
        UUID productId = UUID.randomUUID();
        Product product = org.mockito.Mockito.mock(Product.class);
        given(product.getStatus()).willReturn(Product.Status.ACTIVE);
        given(productRepository.findByIdAndCompanyId(productId, CTX.companyId()))
                .willReturn(Optional.of(product));

        assertThat(productQuery.isSellable(CTX, productId)).isTrue();
    }

    @Test
    @DisplayName("판매 중지 상품은 새 견적에 추가할 수 없다 (PR-06)")
    void isSellable_discontinued_false() {
        UUID productId = UUID.randomUUID();
        Product product = org.mockito.Mockito.mock(Product.class);
        given(product.getStatus()).willReturn(Product.Status.DISCONTINUED);
        given(productRepository.findByIdAndCompanyId(productId, CTX.companyId()))
                .willReturn(Optional.of(product));

        assertThat(productQuery.isSellable(CTX, productId)).isFalse();
    }

    @Test
    @DisplayName("판매 중지 상품도 get()은 값을 돌려준다 — 기존 견적 조회가 깨지면 안 된다 (PR-07)")
    void get_discontinued_stillReturns() {
        UUID productId = UUID.randomUUID();
        Product product = org.mockito.Mockito.mock(Product.class);
        given(product.getId()).willReturn(productId);
        given(product.getName()).willReturn("메쉬 의자");
        given(product.getUnit()).willReturn("개");
        given(product.getUnitPrice()).willReturn(95_000L);
        given(productRepository.findByIdAndCompanyId(productId, CTX.companyId()))
                .willReturn(Optional.of(product));

        assertThat(productQuery.get(CTX, productId).name()).isEqualTo("메쉬 의자");
    }

    @Test
    @DisplayName("없는 상품은 예외가 아니라 false — 존재 여부를 노출하지 않는다 (SC-09)")
    void isSellable_missing_false() {
        UUID productId = UUID.randomUUID();
        given(productRepository.findByIdAndCompanyId(productId, CTX.companyId()))
                .willReturn(Optional.empty());

        assertThat(productQuery.isSellable(CTX, productId)).isFalse();
    }
}
