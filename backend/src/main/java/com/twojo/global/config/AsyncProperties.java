package com.twojo.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비동기 실행기 크기 — {@code twojo.async.*}로 프로필별 조정 (docs/14-tech-stack.md §1.2).
 *
 * <p><b>기본값은 보수적이다.</b> 여기 실리는 것은 커밋 후 메일 발송(NT-01~06) 하나뿐이고,
 * 현재 예상 발송량과 단일 인스턴스 운영을 기준으로 잡은 값이다. 환경별로 조정한다 —
 * blocking I/O라 해도 스레드 수는 처리량에 그대로 영향을 주므로, 발송량이 늘면
 * {@code twojo.async.*}로 올리는 것이 정상 경로다.
 *
 * <p><b>{@code queueCapacity}가 {@code maxPoolSize}보다 중요하다.</b>
 * {@link java.util.concurrent.ThreadPoolExecutor}는 <b>큐가 가득 찬 뒤에야</b>
 * 스레드를 core 위로 늘린다. 큐를 크게 잡으면 max는 사실상 도달하지 않는 숫자가 되고,
 * 포화는 스레드가 아니라 힙에 쌓인다. 그래서 큐를 {@value #DEFAULT_QUEUE_CAPACITY}로
 * 낮게 잡아 <b>증설 지점이 실제로 닿는 값</b>이 되게 했다.
 *
 * <p><b>기본값은 미주입({@code null})에만 적용한다.</b> 그래서 필드가 {@code int}가 아니라
 * {@link Integer}다 — primitive면 미주입도 {@code 0}으로 들어와, 사용자가 명시한 {@code 0}과
 * 구별할 수 없다. 그 상태로 "0이면 기본값"을 적용하면 <b>잘못된 설정이 조용히 정상값으로
 * 바뀌어</b> 기동한다. 아래 규칙과 정면으로 어긋난다.
 *
 * <p>값이 <b>명시적으로</b> 잘못 주입되면 기동을 실패시킨다 — 조용히 도는 것보다 즉시
 * 드러나는 편이 낫다 (14-tech-stack.md §2-8과 같은 태도).
 * {@code queueCapacity = 0}(직접 인계 {@code SynchronousQueue} 모드)도 <b>이 알림 실행기에서는
 * 지원하지 않는다</b> — 큐가 없으면 발송이 몰리는 순간 곧바로 거부로 떨어져, 큐를 완충으로
 * 두려는 이 설정의 목적과 맞지 않는다.
 *
 * @param corePoolSize     상시 스레드 수. 1 이상
 * @param maxPoolSize      상한. 1 이상이고 {@code corePoolSize} 이상이어야 한다
 * @param queueCapacity    대기 큐. 1 이상 — 유한해야 하고(무한 큐는 OOM 경로),
 *                         0(SynchronousQueue)도 허용하지 않는다
 * @param awaitTermination 종료 시 진행 중 작업을 기다리는 시간. 0보다 커야 한다
 *                         ({@code server.shutdown: graceful}과 짝)
 */
@ConfigurationProperties(prefix = "twojo.async")
public record AsyncProperties(
        Integer corePoolSize,
        Integer maxPoolSize,
        Integer queueCapacity,
        Duration awaitTermination) {

    private static final int DEFAULT_CORE_POOL_SIZE = 2;
    private static final int DEFAULT_MAX_POOL_SIZE = 4;
    private static final int DEFAULT_QUEUE_CAPACITY = 100;
    private static final Duration DEFAULT_AWAIT_TERMINATION = Duration.ofSeconds(30);

    public AsyncProperties {
        // 기본값은 미주입에만 — 명시된 값은 그대로 검증 대상으로 넘긴다.
        // 삼항 연산자를 쓰지 않는 이유는 Integer와 int를 섞으면 언박싱 후 곧바로 재박싱되기 때문이다
        // (SpotBugs BX_UNBOXING_IMMEDIATELY_REBOXED).
        if (corePoolSize == null) {
            corePoolSize = DEFAULT_CORE_POOL_SIZE;
        }
        if (maxPoolSize == null) {
            maxPoolSize = DEFAULT_MAX_POOL_SIZE;
        }
        if (queueCapacity == null) {
            queueCapacity = DEFAULT_QUEUE_CAPACITY;
        }
        if (awaitTermination == null) {
            awaitTermination = DEFAULT_AWAIT_TERMINATION;
        }

        if (corePoolSize < 1 || maxPoolSize < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException(
                    "twojo.async: core·max·queue는 1 이상이어야 합니다 (core=%d, max=%d, queue=%d)"
                            .formatted(corePoolSize, maxPoolSize, queueCapacity));
        }
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                    "twojo.async: max-pool-size는 core-pool-size 이상이어야 합니다 (core=%d, max=%d)"
                            .formatted(corePoolSize, maxPoolSize));
        }
        if (awaitTermination.isNegative() || awaitTermination.isZero()) {
            throw new IllegalArgumentException(
                    "twojo.async.await-termination은 0보다 커야 합니다: " + awaitTermination);
        }
    }
}
