package com.twojo.global.error;

import java.util.List;

/**
 * 공통 에러 응답 계약 (docs/08-dto.md §0 · docs/11-work-breakdown.md §1.3).
 * <p>traceId 필드는 없다 — v1.6 계약은 fieldErrors다. traceId의 실거처는 로그·헤더(14-tech-stack.md §1.5).
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String reason) {}

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors);
    }
}
