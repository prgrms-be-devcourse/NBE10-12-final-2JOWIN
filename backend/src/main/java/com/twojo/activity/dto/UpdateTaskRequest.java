package com.twojo.activity.dto;

import java.time.LocalDate;

/**
 * 할 일 수정·완료 처리 요청.
 *
 * <p><b>PATCH: null 필드는 미변경</b> — {@code done} 하나만 토글하는 완료 처리가 주 용도라
 * 부분 수정으로 확정했다 (B 공유문서 1번). {@code Boolean}이 래퍼 타입인 이유가 이것이다 —
 * {@code boolean}이면 안 보냈을 때와 {@code false}를 구별할 수 없다.
 */
public record UpdateTaskRequest(
        String content,
        LocalDate dueDate,
        Boolean done) {}
