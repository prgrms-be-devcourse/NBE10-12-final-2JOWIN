package com.twojo.customer.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PATCH 부분 수정 규약 — NOT NULL 컬럼(name·email)만 공백을 막는다 (08 §B). */
class UpdateContactRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("전화번호만 보내도 통과한다 — 이름·이메일을 함께 요구하지 않는다")
    void phoneOnly_passes() {
        assertThat(VALIDATOR.validate(new UpdateContactRequest(null, null, "010-0000-0000", null))).isEmpty();
    }

    @Test
    @DisplayName("빈 이름·빈 이메일은 거절한다 — NOT NULL 컬럼이라 지울 수 없다")
    void blankRequired_rejected() {
        assertThat(VALIDATOR.validate(new UpdateContactRequest("  ", null, null, null))).hasSize(1);
        assertThat(VALIDATOR.validate(new UpdateContactRequest(null, null, null, ""))).hasSize(1);
    }

    @Test
    @DisplayName("형식이 틀린 이메일은 거절한다 — @Pattern이 @Email을 가리지 않는다")
    void malformedEmail_rejected() {
        assertThat(VALIDATOR.validate(new UpdateContactRequest(null, null, null, "abc"))).hasSize(1);
    }

    @Test
    @DisplayName("직책·전화번호는 빈 문자열로 비울 수 있다 — nullable 컬럼이다")
    void blankOptional_passes() {
        assertThat(VALIDATOR.validate(new UpdateContactRequest(null, "", "", null))).isEmpty();
    }
}
