package com.twojo.activity.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PATCH 부분 수정 규약 — NOT NULL 컬럼(content)만 공백을 막는다 (08 §B). */
class UpdateTaskRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("완료 처리만 보내도 통과하고, 빈 내용은 거절한다")
    void task() {
        assertThat(VALIDATOR.validate(new UpdateTaskRequest(null, null, true))).isEmpty();
        assertThat(VALIDATOR.validate(new UpdateTaskRequest(null, LocalDate.of(2026, 9, 10), null))).isEmpty();

        assertThat(VALIDATOR.validate(new UpdateTaskRequest("   ", null, null))).hasSize(1);
    }
}
