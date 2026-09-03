package com.twojo.activity.dto;

import java.time.LocalDate;

/**
 * 할 일 수정·완료 처리 요청.
 *
 * <p><b>PATCH: null 필드는 미변경</b> — {@code done} 하나만 토글하는 완료 처리가 주 용도라
 * 부분 수정으로 확정했다 (B 공유문서 1번). {@code Boolean}이 래퍼 타입인 이유는 안 보낸 것과
 * {@code false}를 구별해 두기 위해서다 — 아래 미결이 어느 쪽으로 정해지든 받아낼 수 있다.
 *
 * <p><b>{@code done = false}의 처리는 아직 정하지 않았다</b> — 활동이력 API 이슈에서 확정한다.
 * 완료 취소는 AC-09에도 07 명세에도 없고 거절할 에러 코드도 없어, 현재 방향은 미변경으로
 * 흘리는 쪽이다. 되돌리기를 지원한다면 {@code Task.reopen()}과 에러 코드가 함께 가야 한다.
 */
public record UpdateTaskRequest(
        String content,
        LocalDate dueDate,
        Boolean done) {}
