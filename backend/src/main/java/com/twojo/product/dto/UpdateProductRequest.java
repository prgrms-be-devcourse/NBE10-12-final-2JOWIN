package com.twojo.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 상품 수정 요청 (PR-04·08) — 기업 관리자만 (PR-09).
 *
 * <p><b>온 값을 그대로 반영한다</b> — 08 §B에 "null 필드는 미변경" 주석이 없고
 * 설명을 뺀 셋이 전부 필수라, 수정 폼이 기존 값을 채워 전체를 보내는 것을 전제한다.
 *
 * <p>단가·이름을 바꿔도 <b>기존 견적은 영향받지 않는다</b> — 견적이 작성 시점에 값을
 * 복사해 두기 때문이다 (QT-24, PR-07·08). 이름을 바꿀 때는 중복 검사가 필요하다.
 */
public record UpdateProductRequest(
        @NotBlank String name,
        @NotBlank String unit,
        @NotNull @PositiveOrZero Long unitPrice,
        String description) {}
