package com.twojo.deal;

import com.twojo.boundary.AuditActor;
import java.time.Instant;
import java.util.UUID;

/**
 * Deal 단계 전이 — 감사 자동 기록(AC-07)의 입력. payload 규약은 이슈 #22 3번 표다.
 *
 * <p><b>모듈 루트에 있는 이유</b> — 이 패키지의 {@code package-info}가 "타 모듈은 루트의 공개만
 * 사용한다. 내부 구현은 하위 패키지에 두어 차단한다"로 정하고 있어, 루트는 <b>선언 없이</b>
 * 열려 있는 자리다. #22 초안이 제안한 {@code com.twojo.deal.event}도 <b>가능하다</b> —
 * A의 {@code com.twojo.member.event}가 {@code @NamedInterface("event")}로 그 길을 쓰고 있다
 * (#215). 하위 패키지 자체가 막히는 것이 아니라 <b>노출 선언이 없을 때</b> 막힌다.
 *
 * <p>그럼에도 루트를 고른 것은 <b>지울 선언이 없기 때문</b>이다. 노출 선언은 지워져도
 * 구독자가 없는 동안에는 {@code ModularityTests}가 잡지 못한다 — 그 검증은 실제 참조가
 * 있어야 위반을 보기 때문이고, A가 그 회귀를 막으려고 {@code MemberModuleExposureTest}를
 * 따로 두었다. 루트에는 그 실패 모드가 없다.
 *
 * <p><b>지금 저장소에는 두 방식이 함께 있다</b> — member는 하위 패키지 + 노출 선언, C의
 * 이벤트 6종은 루트다. 통일 여부는 미정이고, 기능에는 영향이 없다 (B 확인, PR #268).
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
