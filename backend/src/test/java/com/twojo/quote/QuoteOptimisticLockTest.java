package com.twojo.quote;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 낙관적 락 동시성 검증 — <b>C의 대표 Evidence</b> (11-work-breakdown.md §4).
 *
 * <p>{@code quote}·{@code deal}만 {@code @Version}을 가진다 (업무 분담 §1.1).
 * 수정·전이 요청에는 version이 실려 오고, 불일치는 409 STALE_VERSION이다 (검증 노트 #4).
 *
 * <p><b>Deal 전이 쪽은 {@code DealStageConcurrencyTest}로 옮겨 구현했다</b> (#61) —
 * 실제 DB가 있어야 재현되는 검증이라 단위 테스트 자리에 둘 수 없었다.
 * 여기 남은 것은 견적 수정으로, 견적 CRUD 이슈에서 같은 방식으로 활성화한다.
 */
class QuoteOptimisticLockTest {

    @Test
    @Disabled("견적 CRUD 구현 이슈에서 활성화 — PUT /quotes/{id}가 아직 없다")
    @DisplayName("같은 견적을 동시에 수정하면 나중 요청이 STALE_VERSION으로 막힌다")
    void 동시_수정은_한쪽만_성공한다() {
        // given: DRAFT 견적 1건 (version = 0)
        // when : 두 스레드가 같은 version = 0으로 PUT /api/v1/quotes/{id} 호출
        // then : 한쪽만 성공(version = 1) · 나머지는 STALE_VERSION(409)
        //        사용자 안내는 "새로고침 후 다시 시도" (API 명세서 부록)
    }
}
