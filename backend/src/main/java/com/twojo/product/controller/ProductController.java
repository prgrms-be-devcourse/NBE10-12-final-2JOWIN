package com.twojo.product.controller;

import com.twojo.boundary.AccessContext;
import com.twojo.global.response.PageResponse;
import com.twojo.product.dto.CreateProductRequest;
import com.twojo.product.dto.ProductResponse;
import com.twojo.product.dto.UpdateProductRequest;
import com.twojo.product.entity.Product;
import com.twojo.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 카탈로그 (07 §B · PR).
 *
 * <p>{@link AccessContext}는 인증 필터가 심은 principal에서 타입으로 주입된다 (PR #30).
 * 역할 판정(PR-09)은 서비스가 한다. 삭제 엔드포인트는 없다 — 판매 중지로 대체한다 (11 §1.5).
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    /** Q-39 — 0-base · 기본 20 · 최대 100(초과 시 절삭) */
    private static final int MAX_PAGE_SIZE = 100;

    /** 카탈로그는 찾는 대상이라 이름순이다 — 딜·활동의 {@code createdAt DESC}와 다르다 (Q-39). */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final ProductService productService;

    /** 목록 (PR-03·10) — 전 구성원. {@code status}를 비우면 판매 중지 상품도 함께 나온다. */
    @GetMapping
    public PageResponse<ProductResponse> list(
            AccessContext ctx,
            @RequestParam(required = false) Product.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.list(ctx, status, pageable(page, size));
    }

    /** 등록 (PR-01·02) — 기업 관리자만. 이름은 회사 내 유일하다 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(AccessContext ctx, @Valid @RequestBody CreateProductRequest request) {
        return productService.create(ctx, request);
    }

    /** 수정 (PR-04·08) — 기업 관리자만. null 필드는 미변경 (08 §B) */
    @PatchMapping("/{productId}")
    public ProductResponse update(AccessContext ctx, @PathVariable UUID productId,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(ctx, productId, request);
    }

    /** 판매 중지 (PR-05) — 기업 관리자만. 바뀐 상품을 돌려줘 프론트가 재조회하지 않게 한다 */
    @PostMapping("/{productId}/discontinue")
    public ProductResponse discontinue(AccessContext ctx, @PathVariable UUID productId) {
        return productService.discontinue(ctx, productId);
    }

    /** 판매 재개 — 기업 관리자만. 중지한 이름은 재등록이 막히므로 이쪽이 정식 경로다 */
    @PostMapping("/{productId}/reactivate")
    public ProductResponse reactivate(AccessContext ctx, @PathVariable UUID productId) {
        return productService.reactivate(ctx, productId);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
    }
}
