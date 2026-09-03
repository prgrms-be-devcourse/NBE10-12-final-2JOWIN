package com.twojo.activity.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PATCH 부분 수정 규약 — NOT NULL 컬럼만 공백을 막는다 (08 §B). */
class UpdateActivityRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("상담 기록 — 내용만 보내도 통과하고, 빈 채널·빈 내용은 거절한다")
    void activity() {
        assertThat(VALIDATOR.validate(new UpdateActivityRequest(null, "다시 통화함", null))).isEmpty();

        assertThat(VALIDATOR.validate(new UpdateActivityRequest("  ", null, null))).hasSize(1);
        assertThat(VALIDATOR.validate(new UpdateActivityRequest(null, "", null))).hasSize(1);
    }

    @Test
    @DisplayName("상담 기록 — 발생 시각만 바꾸는 요청도 통과한다")
    void activity_occurredAtOnly() {
        assertThat(VALIDATOR.validate(
                new UpdateActivityRequest(null, null, Instant.parse("2026-09-01T00:00:00Z")))).isEmpty();
    }
}
