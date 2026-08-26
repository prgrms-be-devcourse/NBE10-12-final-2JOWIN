package com.twojo.global.error;

import lombok.Getter;

/**
 * 도메인 규칙 위반의 공통 예외 — ErrorCode 하나가 곧 응답이 된다.
 * <p>상태 전이표(docs/05-state-transitions.md)의 "막히는 것" = 이 예외 하나로 던진다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
