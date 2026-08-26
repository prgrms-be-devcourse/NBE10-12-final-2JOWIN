package com.twojo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * 모듈 경계 검증 (docs/11-work-breakdown.md §7.3).
 *
 * <p>이 테스트가 잡는 것:
 * <ul>
 *   <li>타 모듈 내부 패키지(internal 등) 직접 참조 — "타인 Repository 주입 금지"의 실체</li>
 *   <li>모듈 간 순환 의존 — 단, 문서상 허용된 인터페이스 경유 호출(B↔C·C↔D)은
 *       공개 API(모듈 루트) 참조라 통과한다</li>
 * </ul>
 * CI build 잡이 required check이므로, 위반 = 머지 차단.
 */
class ModularityTests {

    ApplicationModules modules = ApplicationModules.of(BackendApplication.class);

    @Test
    void verifyModuleBoundaries() {
        modules.verify();
    }
}
