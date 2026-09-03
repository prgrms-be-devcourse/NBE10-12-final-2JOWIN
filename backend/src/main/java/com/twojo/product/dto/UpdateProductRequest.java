package com.twojo.product.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 상품 수정 요청 (PR-04·08) — 기업 관리자만 (PR-09).
 *
 * <p><b>PATCH: null 필드는 미변경</b> (08 §B 주석) — 단가 하나만 고치는 요청이
 * 이름·단위까지 함께 보내도록 강요하지 않는다.
 *
 * <p>{@code name}·{@code unit}에 {@code @NotBlank} 대신 {@code @Pattern}을 쓴다 —
 * Bean Validation은 {@code @Pattern}·{@code @PositiveOrZero}에서 null을 검사하지 않으므로
 * "안 보내는 건 되고, 보냈으면 공백은 안 된다"가 그대로 표현된다.
 *
 * <p>단가·이름을 바꿔도 <b>기존 견적은 영향받지 않는다</b> — 견적이 작성 시점에 값을
 * 복사해 두기 때문이다 (QT-24, PR-07·08). 이름을 바꿀 때는 중복 검사가 필요하다.
 */
public record UpdateProductRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String name,
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") String unit,
        @PositiveOrZero Long unitPrice,
        String description) {}
