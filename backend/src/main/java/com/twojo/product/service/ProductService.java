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
 * <p>역할 위반은 403, 타사·미존재 리소스는 404다 (Q-43 · SC-09).
 * 삭제는 없다 — 판매 중지로 대체한다 (11 §1.5).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /** 목록 (PR-03·10) — 전 구성원. {@code status}가 null이면 판매 중지 상품도 함께 나온다. */
    public PageResponse<ProductResponse> list(AccessContext ctx, Product.Status status, Pageable pageable) {
        return PageResponse.from(
                status == null
                        ? productRepository.findByCompanyId(ctx.companyId(), pageable).map(ProductResponse::of)
                        : productRepository.findByCompanyIdAndStatus(ctx.companyId(), status, pageable)
                                .map(ProductResponse::of));
    }

    /** 등록 (PR-01·02) — 이름은 회사 내 유일하다 (판매 중지 포함). */
    @Transactional
    public ProductResponse create(AccessContext ctx, CreateProductRequest request) {
        requireAdmin(ctx);

        if (productRepository.existsByCompanyIdAndName(ctx.companyId(), request.name())) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }

        Product product = Product.create(ctx.companyId(), request.name(), request.unit(),
                request.unitPrice(), request.description());
        return ProductResponse.of(saveOrConflict(product));
    }

    /**
     * 수정 (PR-04·08) — null로 온 필드는 미변경이다 (08 §B).
     * 단가·이름을 바꿔도 기존 견적은 안 움직인다 — 견적이 값을 복사해 두기 때문이다 (QT-24).
     */
    @Transactional
    public ProductResponse update(AccessContext ctx, UUID productId, UpdateProductRequest request) {
        requireAdmin(ctx);
        Product product = findInScope(ctx, productId);

        // 이름을 안 보냈으면 검사하지 않는다 — 07 §B "이름 변경 시 중복 검사"
        if (request.name() != null
                && productRepository.existsByCompanyIdAndNameAndIdNot(ctx.companyId(), request.name(), productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }

        product.update(request.name(), request.unit(), request.unitPrice(), request.description());
        return ProductResponse.of(saveOrConflict(product));
    }

    /** 판매 중지 (PR-05) — 이미 중지된 상품이면 무동작이다. 기존 견적에는 영향이 없다 (PR-07). */
    @Transactional
    public ProductResponse discontinue(AccessContext ctx, UUID productId) {
        requireAdmin(ctx);
        Product product = findInScope(ctx, productId);
        product.discontinue();
        return ProductResponse.of(product);
    }

    /** 판매 재개 — 중지한 이름은 UNIQUE에 걸려 재등록이 막히므로 이쪽이 정식 경로다. */
    @Transactional
    public ProductResponse reactivate(AccessContext ctx, UUID productId) {
        requireAdmin(ctx);
        Product product = findInScope(ctx, productId);
        product.reactivate();
        return ProductResponse.of(product);
    }

    /**
     * 카탈로그 편집은 기업 관리자만 (PR-09).
     * 컨트롤러가 아니라 여기서 판정한다 — 웹 계층 검사는 다른 호출 경로가 생기면 뚫린다.
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
     * 사전 검사를 통과했는데도 DB UNIQUE에 걸리는 동시 요청을 409로 바꾼다.
     *
     * <p><b>{@code save()}가 아니라 {@code saveAndFlush()}다.</b> {@code save()}는 INSERT를
     * 커밋 시점까지 미루므로 예외가 이 try 블록 밖에서 터져 500이 된다.
     *
     * <p>{@code GlobalExceptionHandler}가 아니라 여기서 잡는다 — 그 예외는 모든 도메인의 모든
     * 제약 위반에서 나므로, 어느 제약인지 아는 자리에서 잡아야 한다.
     */
    private Product saveOrConflict(Product product) {
        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }
    }
}
