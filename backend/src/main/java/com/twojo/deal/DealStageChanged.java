package com.twojo.deal;

import com.twojo.boundary.AuditActor;
import java.time.Instant;
import java.util.UUID;

/**
 * Deal 단계 전이 — 감사 자동 기록(AC-07)의 입력. payload 규약은 이슈 #22 3번 표다.
 *
 * <p><b>모듈 루트에 있는 이유</b>는 이 패키지의 {@code package-info}가 정한 규칙 때문이다 —
 * "타 모듈은 이 패키지 루트의 공개만 사용한다. 내부 구현은 하위 패키지에 두어 차단한다."
 * #22 초안은 {@code com.twojo.deal.event}를 제안했지만, 하위 패키지에 두면 소비자(B)가
 * import하는 순간 {@code ModularityTests}가 깨진다. 이 저장소는 {@code @NamedInterface}를
 * 쓰지 않으므로 루트가 유일한 공개 자리다.
 *
 * <p><b>단계가 실제로 바뀐 경우에만 발행한다.</b> 자동 승급(Q-25)·자동 성사(OD-06)는 멱등이라
 * 이미 그 단계면 아무 일도 하지 않는데, 그때도 발행하면 타임라인에 "견적 → 견적" 같은
 * 일어나지 않은 변화가 쌓인다.
 *
 * @param actor      수동 이동은 {@code MEMBER}, 자동 승급·성사는 {@code SYSTEM} (#22 3번 표)
 * @param lostReason <b>실패(LOST) 전이일 때만</b> 값이 있고 그 외에는 null (DL-11).
 *                   {@code deal.lost_reason}은 C 소유라 B가 읽을 수 없어, 타임라인에
 *                   "실패 처리 — 사유: 예산 부족"을 띄우려면 payload로 받아야 한다 (C 요청 2026-08-31)
 */
public record DealStageChanged(
        UUID companyId, UUID dealId,
        AuditActor actor, Instant occurredAt,
        String beforeStage, String afterStage,
        String lostReason) {
}
