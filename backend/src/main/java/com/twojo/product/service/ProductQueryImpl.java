package com.twojo.product.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.ProductQuery;
import com.twojo.product.entity.Product;
import com.twojo.product.repository.ProductRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ProductQuery} 구현 — 타 도메인이 카탈로그 값을 얻는 유일한 통로다 (docs/11 §7.2).
 *
 * <p>{@link #get}이 돌려주는 값이 견적 항목에 <b>복사</b>되어 저장된다 (QT-24). 복사가 끝난 뒤에는
 * 카탈로그를 고쳐도 과거 견적 금액이 움직이지 않는다 — PR-07·08이 요구하는 성질이 여기서 시작된다.
 *
 * <p>{@link #get}과 {@link #isSellable}은 서로 다른 규칙을 담당한다.
 * <ul>
 *   <li>{@code isSellable} — PR-06: 판매 중지 상품을 <b>새 견적에 추가</b>하지 못하게 한다</li>
 *   <li>{@code get} — PR-07: 판매 중지가 <b>이미 작성된 견적</b>에 영향을 주지 않아야 하므로
 *       상태와 무관하게 값을 돌려준다. 여기서 막으면 그 상품이 들어간 과거 견적 조회가 깨진다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ProductQueryImpl implements ProductQuery {

    private final ProductRepository productRepository;

    @Override
    public ProductSnapshot get(AccessContext ctx, UUID productId) {
        return productRepository.findByIdAndCompanyId(productId, ctx.companyId())
                .map(product -> new ProductSnapshot(
                        product.getId(), product.getName(), product.getUnit(), product.getUnitPrice()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 없는 상품·타사 상품도 {@code false} — 판매 가능 여부만 답하고 존재 여부는 노출하지 않는다 (SC-09). */
    @Override
    public boolean isSellable(AccessContext ctx, UUID productId) {
        return productRepository.findByIdAndCompanyId(productId, ctx.companyId())
                .map(product -> product.getStatus() == Product.Status.ACTIVE)
                .orElse(false);
    }
}
