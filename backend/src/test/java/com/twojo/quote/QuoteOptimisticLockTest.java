package com.twojo.quote;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 낙관적 락 동시성 검증 — <b>C의 대표 Evidence</b> (11-work-breakdown.md §4).
 *
 * <p>1주차에 선작성한다. 견적 수정 API가 없어 지금은 {@code @Disabled}이며,
 * 견적 CRUD 이슈에서 활성화한다.
 *
 * <p>{@code quote}·{@code deal}만 {@code @Version}을 가진다 (업무 분담 §1.1).
 * 수정·전이 요청에는 version이 실려 오고, 불일치는 409 STALE_VERSION이다 (검증 노트 #4).
 */
@Disabled("견적 CRUD 구현 이슈에서 활성화 — TODO: 이슈 번호 기입")
class QuoteOptimisticLockTest {

    @Test
    @DisplayName("같은 견적을 동시에 수정하면 나중 요청이 STALE_VERSION으로 막힌다")
    void 동시_수정은_한쪽만_성공한다() {
        // given: DRAFT 견적 1건 (version = 0)
        // when : 두 스레드가 같은 version = 0으로 PUT /api/v1/quotes/{id} 호출
        // then : 한쪽만 성공(version = 1) · 나머지는 STALE_VERSION(409)
        //        사용자 안내는 "새로고침 후 다시 시도" (API 명세서 부록)
    }

    @Test
    @DisplayName("Deal 단계 전이도 version 불일치를 막는다 (DL-07·08)")
    void 동시_단계_전이는_한쪽만_성공한다() {
        // given: LEAD 상태 Deal (version = 0)
        // when : 두 스레드가 같은 version으로 advance 호출
        // then : 한쪽만 CONSULT로 이동 · 나머지는 STALE_VERSION
        //        표에 없는 전이(단계 건너뛰기)가 생기지 않는지 함께 확인 (전이표 §5)
    }
}
