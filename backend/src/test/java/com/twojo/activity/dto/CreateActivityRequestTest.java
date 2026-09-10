package com.twojo.activity.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 상담 수단 값 검증 (AC-02 · 08 §B v1.6.20) — 서비스의 enum 변환이 500으로 터지기 전에 막는다. */
class CreateActivityRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-09T02:00:00Z");

    @Test
    @DisplayName("수단은 CALL·MEETING·EMAIL 중 하나만 통과한다")
    void channel() {
        assertThat(VALIDATOR.validate(new CreateActivityRequest("CALL", "예산 확인", OCCURRED_AT))).isEmpty();
        assertThat(VALIDATOR.validate(new CreateActivityRequest("카카오톡", "예산 확인", OCCURRED_AT))).hasSize(1);
    }

    /**
     * {@code @NotBlank}가 아니라 {@code @NotNull}인 이유 — 빈 문자열에 두 제약이 함께 걸리면
     * {@code fieldErrors}가 같은 필드로 2행이 된다 (08 §B 주석).
     */
    @Test
    @DisplayName("빈 수단은 한 가지 사유로만 거절한다")
    void channel_blank_reportsOneError() {
        assertThat(VALIDATOR.validate(new CreateActivityRequest("", "예산 확인", OCCURRED_AT))).hasSize(1);
    }
}
