package com.twojo.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 우리가 직접 쓴 유효성 검사만 확인한다 — 명시적으로 잘못된 값으로는 뜨지 않는다는 것.
 * 미주입({@code null}) 기본값과 바인딩은 {@link AsyncConfigTest}가 실행기 쪽에서 함께 본다.
 */
class AsyncPropertiesTest {

    private static final Duration THIRTY_SECONDS = Duration.ofSeconds(30);

    @Test
    @DisplayName("명시적으로 잘못된 설정값은 기동을 실패시킨다")
    void invalidConfigurationFailsFast() {
        // 0은 "미주입"이 아니라 잘못된 값이다 — 조용히 기본값으로 바뀌지 않는다
        assertThatThrownBy(() -> new AsyncProperties(0, 4, 100, THIRTY_SECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");

        assertThatThrownBy(() -> new AsyncProperties(2, 0, 100, THIRTY_SECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");

        // queue 0 = SynchronousQueue 직접 인계 — 이 알림 실행기에서는 지원하지 않는다
        assertThatThrownBy(() -> new AsyncProperties(2, 4, 0, THIRTY_SECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");

        assertThatThrownBy(() -> new AsyncProperties(2, 4, -1, THIRTY_SECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");

        // max < core면 ThreadPoolTaskExecutor가 뒤늦게 터진다 — 설정 단계에서 막는다
        assertThatThrownBy(() -> new AsyncProperties(4, 2, 100, THIRTY_SECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("core-pool-size 이상");

        assertThatThrownBy(() -> new AsyncProperties(2, 4, 100, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("await-termination");

        assertThatThrownBy(() -> new AsyncProperties(2, 4, 100, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("await-termination");
    }
}
