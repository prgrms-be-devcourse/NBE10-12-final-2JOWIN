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
 * <p>{@link AccessContext}는 인증 필터가 심은 principal에서 타입으로 주입된다 (PR #30) —
 * 요청에 회사 식별자가 실리지 않는다. <b>역할 판정(PR-09)은 서비스가 한다</b> —
 * 웹 계층에만 걸린 검사는 다른 호출 경로가 생겼을 때 그대로 뚫린다.
 *
 * <p>삭제 엔드포인트가 없다 — 상품은 판매 중지로 대체한다 (11 §1.5).
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    /** Q-39 — 0-base · 기본 20(파라미터 기본값) · 최대 100(초과 시 절삭) */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 목록 기본 정렬은 엔드포인트가 고정한다 — 클라이언트가 지정하지 않는다 (Q-39).
     * 카탈로그는 <b>찾는 대상</b>이라 이름순이다. 딜·활동의 {@code createdAt DESC}와 다른 이유다 —
     * 사용자가 상품을 찾을 때 등록 순서를 기억하지 않는다.
     */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final ProductService productService;

    /**
     * 목록 (PR-03·10) — 전사 공유라 전 구성원이 본다.
     *
     * <p>{@code status}를 비우면 판매 중지 상품도 함께 나온다. 견적 작성 화면처럼 판매 중인 것만
     * 필요한 자리에서는 {@code ?status=ACTIVE}로 거른다.
     */
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

    /** 수정 (PR-04·08) — 기업 관리자만. 단가·이름을 바꿔도 기존 견적은 안 움직인다 (QT-24) */
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

    /** 판매 재개 — 기업 관리자만. 중지한 이름은 재등록이 막히므로(UNIQUE) 이쪽이 정식 경로다 */
    @PostMapping("/{productId}/reactivate")
    public ProductResponse reactivate(AccessContext ctx, @PathVariable UUID productId) {
        return productService.reactivate(ctx, productId);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
    }
}
