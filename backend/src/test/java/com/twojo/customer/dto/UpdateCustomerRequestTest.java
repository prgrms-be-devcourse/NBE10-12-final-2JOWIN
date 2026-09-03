package com.twojo.customer.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PATCH 부분 수정 규약이 애너테이션으로 실제 표현되는지 고정한다 (08 §B).
 *
 * <p>{@code @NotBlank}였다면 null도 거절해 부분 수정이 막힌다. 그 회귀를 막는 테스트다.
 */
class UpdateCustomerRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private UpdateCustomerRequest 이름만(String name) {
        return new UpdateCustomerRequest(name, null, null, null);
    }

    @Test
    @DisplayName("안 보낸 이름(null)은 통과한다 — 비고만 바꾸는 요청이 막히지 않는다")
    void nullName_passes() {
        assertThat(VALIDATOR.validate(new UpdateCustomerRequest(null, null, null, "메모만"))).isEmpty();
        assertThat(VALIDATOR.validate(이름만("도담건설"))).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열·공백만 있는 이름은 거절한다")
    void blankName_rejected() {
        assertThat(VALIDATOR.validate(이름만(""))).hasSize(1);
        assertThat(VALIDATOR.validate(이름만("   "))).hasSize(1);
        assertThat(VALIDATOR.validate(이름만("\t\n"))).hasSize(1);
    }

    @Test
    @DisplayName("줄바꿈이 섞인 이름은 통과한다 — 공백만 거절하는 것이지 개행을 막는 게 아니다")
    void multilineName_passes() {
        assertThat(VALIDATOR.validate(이름만("도담\n건설"))).isEmpty();
    }
}
