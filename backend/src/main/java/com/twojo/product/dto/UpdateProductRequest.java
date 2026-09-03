package com.twojo.product.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 상품 수정 요청 (PR-04·08) — <b>PATCH: null 필드는 미변경</b> (08 §B). 기업 관리자만 (PR-09).
 *
 * <p>설명은 빈 문자열로 비운다. 이름·단위는 NOT NULL이라 비울 수 없다.
 *
 * <p>단가·이름을 바꿔도 <b>기존 견적은 영향받지 않는다</b> — 견적이 작성 시점에 값을
 * 복사해 두기 때문이다 (QT-24, PR-07·08). 이름을 바꿀 때는 중복 검사가 필요하다.
 */
public record UpdateProductRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String name,
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String unit,
        @PositiveOrZero Long unitPrice,
        String description) {}
