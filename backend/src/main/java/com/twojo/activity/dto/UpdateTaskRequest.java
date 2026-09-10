package com.twojo.activity.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 할 일 수정·완료 처리 요청 — <b>PATCH: null 필드는 미변경</b> (08 §B).
 * {@code done} 하나만 토글하는 완료 처리가 주 용도다 (B 공유문서 1번).
 *
 * <p><b>{@code done = false}는 미변경이다.</b> 완료 취소는 AC-09에도 07 명세에도 없고
 * {@code Task}에 되돌리는 메서드도, 거절할 에러 코드도 없다. 지원하려면 그 둘이 함께 들어와야 한다.
 * {@code Boolean}이 래퍼 타입인 이유는 안 보낸 것과 {@code false}를 구별해 두기 위해서다 —
 * 되돌리기가 생기면 이 DTO는 그대로 두고 서비스만 갈라진다.
 */
public record UpdateTaskRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") @Size(max = 500) String content,
        LocalDate dueDate,
        Boolean done) {}
