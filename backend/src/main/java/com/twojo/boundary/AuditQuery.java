package com.twojo.boundary;

import java.time.Instant;
import java.time.LocalDate;
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
     * <p><b>기간은 한국 날짜로 받는다.</b> 시각이 아니라 날짜인 이유는 경계 계산을 이 모듈이
     * 소유하기 때문이다 — {@code occurred_at}이 여기 컬럼이고, 호출자가 KST로 끊어 넘기면 같은
     * 규칙이 모듈마다 한 벌씩 생긴다 ({@code OrderQuery.wonTotalsByQuotes}와 같은 축이다).
     *
     * <p><b>리스너가 붙기 전의 전이는 없다.</b> {@code deal}에 현재 단계 한 칸만 있고 이력 테이블이
     * 없어 과거를 복원할 원천이 없다 — 초기 수치는 실제보다 낮다.
     *
     * <p>읽히지 않는 {@code payload}가 있는 행은 결과에서 빠진다(예외 아님) — 한 행 때문에 기간
     * 전체 집계가 실패하면 안 된다.
     *
     * @param from 전이일 하한(<b>포함</b>) — 한국 날짜다. null을 받지 않는다
     * @param to   전이일 상한(<b>포함</b>) — 그날 끝까지다. 1월을 물으면 {@code 01-31}이다.
     *             내부에서는 다음 날 0시 미만으로 끊어 인접한 두 기간이 겹치지도 비지도 않게 한다
     */
    List<StageChange> stageChanges(UUID companyId, LocalDate from, LocalDate to);

    record StageChange(UUID dealId, String beforeStage, String afterStage, Instant occurredAt) {}
}
