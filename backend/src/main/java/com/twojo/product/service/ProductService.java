package com.twojo.product.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.product.dto.CreateProductRequest;
import com.twojo.product.dto.ProductResponse;
import com.twojo.product.dto.UpdateProductRequest;
import com.twojo.product.entity.Product;
import com.twojo.product.repository.ProductRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 카탈로그 (PR-01~10) — 회사 공유 자원이라 담당 개념이 없고, 편집은 기업 관리자만 가능하다.
 *
 * <p><b>역할 위반은 404가 아니라 403이다</b> (Q-43). 상품 등록 기능이 있다는 건 누구나 알고
 * 등록은 특정 리소스를 지목하지 않으므로, 리소스 존재를 감출 이유가 없다. 반면 타사 상품 조회·수정은
 * 그대로 404다 (SC-09) — 그쪽은 존재 여부가 새면 안 된다.
 *
 * <p>상품에는 삭제가 없다. 판매 중지로 대체한다 (11 §1.5) — 과거 견적이 참조하기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /** 목록 (PR-03·10) — 전 구성원이 볼 수 있다. {@code status}가 null이면 판매 중지 상품도 함께 나온다. */
    public PageResponse<ProductResponse> list(AccessContext ctx, Product.Status status, Pageable pageable) {
        return PageResponse.from(
                status == null
                        ? productRepository.findByCompanyId(ctx.companyId(), pageable).map(ProductService::toResponse)
                        : productRepository.findByCompanyIdAndStatus(ctx.companyId(), status, pageable)
                                .map(ProductService::toResponse));
    }

    /** 등록 (PR-01·02) — 이름은 회사 내 유일하다. */
    @Transactional
    public ProductResponse create(AccessContext ctx, CreateProductRequest request) {
        requireAdmin(ctx);

        if (productRepository.existsByCompanyIdAndName(ctx.companyId(), request.name())) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }

        Product product = Product.create(ctx.companyId(), request.name(), request.unit(),
                request.unitPrice(), request.description());
        return toResponse(saveOrConflict(product));
    }

    /**
     * 수정 (PR-04·08) — 단가·이름을 바꿔도 기존 견적은 안 움직인다.
     * 견적이 작성 시점에 값을 복사해 두기 때문이다 (QT-24, PR-07·08).
     */
    @Transactional
    public ProductResponse update(AccessContext ctx, UUID productId, UpdateProductRequest request) {
        requireAdmin(ctx);
        Product product = findInScope(ctx, productId);

        if (productRepository.existsByCompanyIdAndNameAndIdNot(ctx.companyId(), request.name(), productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }

        product.update(request.name(), request.unit(), request.unitPrice(), request.description());
        return toResponse(saveOrConflict(product));
    }

    /** 판매 중지 (PR-05) — 이미 중지된 상품이면 무동작이다. 기존 견적에는 영향이 없다 (PR-07). */
    @Transactional
    public ProductResponse discontinue(AccessContext ctx, UUID productId) {
        requireAdmin(ctx);
        Product product = findInScope(ctx, productId);
        product.discontinue();
        return toResponse(product);
    }

    /**
     * 판매 재개 — 중지한 이름으로 재등록하면 {@code UNIQUE(company_id, name)}에 걸리므로
     * (중지 상품도 포함) 이쪽이 정식 경로다.
     */
    @Transactional
    public ProductResponse reactivate(AccessContext ctx, UUID productId) {
        requireAdmin(ctx);
        Product product = findInScope(ctx, productId);
        product.reactivate();
        return toResponse(product);
    }

    /**
     * 카탈로그 편집은 기업 관리자만 (PR-09, 권한 매트릭스).
     *
     * <p>컨트롤러가 아니라 여기서 판정한다 — 다른 호출 경로가 생겼을 때 웹 계층에만 걸린 검사는
     * 그대로 뚫린다. 역할로 갈리는 행위의 실패는 404가 아니라 <b>403</b>이다 (Q-43).
     */
    private void requireAdmin(AccessContext ctx) {
        if (ctx.role() != Role.COMPANY_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /** 회사 스코프 조회. 없거나 타사 것이면 404 — 존재 여부를 구별해서 말하지 않는다 (SC-09). */
    private Product findInScope(AccessContext ctx, UUID productId) {
        return productRepository.findByIdAndCompanyId(productId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 사전 검사를 통과했는데도 DB에서 걸리는 경우를 409로 바꾼다 — 두 사람이 동시에 같은 이름으로
     * 등록하면 둘 다 사전 검사를 통과하고 나중 것이 UNIQUE 위반으로 실패한다.
     *
     * <p><b>{@code GlobalExceptionHandler}가 아니라 여기서 잡는다.</b> 그쪽은 global 소유라
     * 승인이 필요하고, {@code DataIntegrityViolationException}은 모든 도메인의 모든 제약 위반에서
     * 나므로 거기서 상품명 중복을 던지면 남의 제약 위반까지 상품 에러가 된다.
     */
    private Product saveOrConflict(Product product) {
        try {
            return productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }
    }

    private static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getUnit(),
                product.getUnitPrice(), product.getDescription(), product.getStatus().name());
    }
}
