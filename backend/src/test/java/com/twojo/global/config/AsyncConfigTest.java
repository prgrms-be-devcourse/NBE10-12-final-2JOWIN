package com.twojo.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 이 설정이 <b>스스로 정한 것</b>만 검증한다 — 풀 크기·거부 정책·기본 실행기 지정.
 * {@code ThreadPoolExecutor}가 큐와 스레드를 어떻게 다루는지, {@code @Async}가 다른 스레드에서
 * 도는지는 라이브러리 동작이라 여기서 다시 확인하지 않는다.
 */
class AsyncConfigTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("설정을 주지 않으면 AsyncProperties의 기본값으로 실행기가 만들어진다")
    void executorUsesDefaultsWhenNothingIsBound() {
        ThreadPoolExecutor pool = poolOf(Map.of());

        assertThat(pool.getCorePoolSize()).isEqualTo(2);
        assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
        assertThat(pool.getQueue().remainingCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("twojo.async.* 를 주면 그 값이 실행기에 실린다")
    void executorReflectsBoundProperties() {
        ThreadPoolExecutor pool = poolOf(Map.of(
                "twojo.async.core-pool-size", 3,
                "twojo.async.max-pool-size", 6,
                "twojo.async.queue-capacity", 50));

        assertThat(pool.getCorePoolSize()).isEqualTo(3);
        assertThat(pool.getMaximumPoolSize()).isEqualTo(6);
        assertThat(pool.getQueue().remainingCapacity()).isEqualTo(50);
    }

    @Test
    @DisplayName("거부를 드러낸다 — 조용히 버리지도, 호출 스레드를 붙잡지도 않는다")
    void rejectionIsSurfacedNotSwallowed() {
        // 내구성은 실행기가 아니라 email_log SCHEDULED + 재처리 배치가 맡는다는 판단의 실체
        assertThat(poolOf(Map.of()).getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    }

    @Test
    @DisplayName("이름 없는 @Async도 이 실행기로 간다 — 무제한 실행기로 새는 경로가 없다")
    void defaultAsyncExecutorIsTheNotificationExecutor() {
        context = contextWith(Map.of());

        assertThat(context.getBean(AsyncConfig.class).getAsyncExecutor())
                .isSameAs(context.getBean(AsyncConfig.NOTIFICATION_EXECUTOR));
    }

    private ThreadPoolExecutor poolOf(Map<String, Object> properties) {
        context = contextWith(properties);
        return context.getBean(AsyncConfig.NOTIFICATION_EXECUTOR, ThreadPoolTaskExecutor.class)
                .getThreadPoolExecutor();
    }

    private AnnotationConfigApplicationContext contextWith(Map<String, Object> properties) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", properties));
        ctx.register(AsyncConfig.class);
        ctx.refresh();
        return ctx;
    }
}
