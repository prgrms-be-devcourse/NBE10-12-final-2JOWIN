package com.twojo.boundary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 감사 이력 조회 계약 — 구현: B(activity 모듈). 타 도메인은 {@code audit_log}를 직접 읽지 않는다.
 * (docs/11-work-breakdown.md §7.2)
 */
public interface AuditQuery {

    /**
     * 기간 안의 단계 전이 이력 — 전환율 집계(DB-07)의 원천이다.
     *
     * <p><b>{@code payload}를 그대로 내보내지 않는다.</b> 저장 형식이 경계를 넘으면 규약이 바뀔 때
     * 소비자가 함께 깨진다. {@code before}·{@code after}를 꺼내 필드로 준다.
     *
     * <p><b>집계하지 않는다.</b> 전환율의 정의와 분모는 소비자 쪽에 있다 — 여기서 세면 같은 규칙이
     * 두 곳에 생긴다. 사실만 돌려주고 세는 것은 호출자가 한다.
     *
     * <p><b>행위자 축이 없다.</b> 자동 승급·자동 성사는 {@code SYSTEM}이라 actor로 담당자별 집계를
     * 하면 그 건들이 통째로 빠진다. {@code dealId}로 담당자를 되짚는다.
     *
     * <p><b>리스너가 붙기 전의 전이는 없다.</b> {@code deal}에 현재 단계 한 칸만 있고 이력 테이블이
     * 없어 과거를 복원할 원천이 없다 — 초기 수치는 실제보다 낮다.
     *
     * <p>읽히지 않는 {@code payload}가 있는 행은 결과에서 빠진다(예외 아님) — 한 행 때문에 기간
     * 전체 집계가 실패하면 안 된다.
     *
     * @param from 하한 — <b>포함</b>이다. null을 받지 않는다
     * @param to   상한 — <b>제외</b>다. 다음 구간의 하한을 그대로 넘기면 겹치지도 비지도 않는다.
     *             1월을 물을 때 상한은 {@code 02-01T00:00}이고, 그 시각에 일어난 전이는 2월에만
     *             들어간다. 상한도 포함하면 경계의 한 건이 두 번 세어지고, 그것을 피하려고 상한을
     *             당기면 그 사이 마이크로초에 일어난 전이가 어느 기간에도 들어가지 않는다
     */
    List<StageChange> stageChanges(UUID companyId, Instant from, Instant to);

    record StageChange(UUID dealId, String beforeStage, String afterStage, Instant occurredAt) {}
}
