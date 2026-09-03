package com.twojo.product.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PATCH 부분 수정 규약이 애너테이션으로 실제 표현되는지 고정한다 (08 §B).
 *
 * <p>{@code @NotBlank}·{@code @NotNull}이었다면 null도 거절해 단가만 바꾸는 요청이 막힌다.
 */
class UpdateProductRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("단가만 보내도 통과한다 — 이름·단위를 함께 요구하지 않는다 (PR-04)")
    void unitPriceOnly_passes() {
        assertThat(VALIDATOR.validate(new UpdateProductRequest(null, null, 27_000L, null))).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열·공백만 있는 이름·단위는 거절한다")
    void blankNameOrUnit_rejected() {
        assertThat(VALIDATOR.validate(new UpdateProductRequest("  ", null, null, null))).hasSize(1);
        assertThat(VALIDATOR.validate(new UpdateProductRequest(null, "", null, null))).hasSize(1);
    }

    @Test
    @DisplayName("줄바꿈이 섞인 이름은 통과한다")
    void multilineName_passes() {
        assertThat(VALIDATOR.validate(new UpdateProductRequest("A4\n복사용지", null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("단가가 음수면 거절한다 — 값이 왔을 때만 걸린다")
    void negativeUnitPrice_rejected() {
        assertThat(VALIDATOR.validate(new UpdateProductRequest(null, null, -1L, null))).hasSize(1);
        assertThat(VALIDATOR.validate(new UpdateProductRequest(null, null, 0L, null))).isEmpty();
    }
}
