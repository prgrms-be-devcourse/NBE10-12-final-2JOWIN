package com.twojo.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * 세션 폐기 훅 — 회사 정지·구성원 비활성화의 "즉시 차단"을 실제로 수행하는 진입점
 * (11 §2 · 전이표 §9 · ON-09 · MB-10).
 *
 * <p>auth 패키지 루트에 둔다. 소비자가 onboarding·member 둘뿐이라 전 도메인이 보는
 * boundary에 올릴 이유가 없고, auth/package-info가 "타 모듈은 이 패키지 루트의 공개
 * 인터페이스만 사용한다"고 규정한다. auth는 두 모듈을 직접 참조하지 않으므로 순환도 없다.
 *
 * <p>폐기 사유를 인자로 받지 않고 상황별로 메서드를 나눴다. RevokedReason은 auth.entity,
 * 즉 모듈 내부라 시그니처에 노출할 수 없다. 부르는 쪽이 사유를 고를 일도 없다.
 *
 * <p>이름을 onXxx로 두지 않았다 — Modulith의 @ApplicationModuleListener 관례와 같은 모양이라
 * 이벤트 발행이나 비동기로 오해될 수 있다. 이 둘은 호출자의 트랜잭션에서 동기로 돈다.
 */
public interface SessionRevoker {

    /** 구성원 비활성화 — 해당 구성원의 전 행 (MB-09·10 "즉시 차단"의 실체) */
    void revokeOnDeactivation(UUID memberId, Instant now);

    /** 회사 정지 — 해당 회사 전 구성원의 전 행 (ON-08·09) */
    void revokeOnSuspension(UUID companyId, Instant now);
}
