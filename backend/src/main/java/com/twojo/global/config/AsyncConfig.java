package com.twojo.global.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행 기반 — 커밋 후 메일 발송(NT-01~06)이 요청 스레드를 붙잡지 않게 한다.
 *
 * <p><b>여기가 제공하는 것은 실행 기반뿐이다.</b> 메일 발송·재시도·{@code email_log} 적재는
 * D 소유다 (docs/11-work-breakdown.md §5).
 *
 * <h2>작업의 내구성은 이 실행기가 보장하지 않는다</h2>
 *
 * <p>스레드 풀은 <b>휘발성</b>이다 — 큐에 든 작업은 서버가 죽으면 함께 사라지고,
 * 거부되면 애초에 실행되지 않는다. 따라서 "메일이 언젠가는 나간다"를 보장하는 것은
 * 실행기가 아니라 <b>{@code email_log}의 상태와 재처리 배치</b>다.
 * D가 지켜야 하는 구조는 다음과 같다:
 *
 * <ol>
 *   <li>원래 발송 트랜잭션 안에서 {@code email_log = SCHEDULED} 행을 <b>먼저</b> 저장한다
 *       — 이 행이 유일한 내구 기록이다</li>
 *   <li><b>동기</b> {@code AFTER_COMMIT} 리스너가 <b>별도 Bean</b>의 비동기 메서드를 호출한다</li>
 *   <li>제출이 거부되면 {@code TaskRejectedException}이 그 호출 지점에서 튀어나온다.
 *       <b>동기 리스너가 잡아서 HTTP 응답으로 전파하지 않는다</b> — 커밋은 이미 끝났고
 *       사용자의 발송 요청은 성공한 것이 맞다</li>
 *   <li>거부됐든 서버가 내려갔든, 남아 있는 {@code SCHEDULED} 행을 배치가 다시 집어간다</li>
 *   <li>발송 결과를 {@code SENT}/{@code FAILED}로 기록하는 DB 작업은
 *       <b>새 트랜잭션</b>({@code REQUIRES_NEW})으로 연다 — {@code AFTER_COMMIT} 시점에는
 *       원래 트랜잭션의 동기화 정리가 아직 끝나지 않았을 수 있어, 기존 트랜잭션에
 *       합류하려 들면 커밋되지 않거나 예외가 난다</li>
 * </ol>
 *
 * <p><b>리스너와 dispatcher는 반드시 다른 Bean이어야 한다.</b> 한 메서드에
 * {@code @Async}와 {@code @TransactionalEventListener}를 함께 붙이면 <b>제출 자체가
 * 프록시에서 일어나므로</b> 거부 예외가 메서드 본문 밖에서 발생한다 — 그 메서드 안의
 * {@code try/catch}로는 절대 잡을 수 없고, 예외는 커밋 직후 경로를 타고 요청으로 올라간다.
 * 같은 클래스 안에서 자기 메서드를 부르는 것도 안 된다 — 자기 호출은 프록시를 지나지 않아
 * {@code @Async}가 통째로 무시되고 발송이 요청 스레드에서 동기 실행된다.
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class MailScheduledListener {
 *
 *     private final NotificationDispatcher notificationDispatcher;
 *
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     public void handle(MailScheduled event) {
 *         try {
 *             notificationDispatcher.dispatch(event.emailLogId());
 *         } catch (TaskRejectedException e) {
 *             // HTTP 응답으로 다시 전파하지 않는다.
 *             // email_log는 SCHEDULED로 남고 재처리 배치가 가져간다.
 *             // 조용히 삼키지는 않는다 — 민감정보 없는 로그·지표를 남긴다.
 *         }
 *     }
 * }
 *
 * @Component
 * @RequiredArgsConstructor
 * public class NotificationDispatcher {
 *
 *     @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
 *     @Transactional(propagation = Propagation.REQUIRES_NEW)
 *     public void dispatch(UUID emailLogId) {
 *         // emailLogId로 필요한 정보를 새로 조회한다.
 *         // 엔티티·SecurityContext·요청 스코프를 전달받지 않는다.
 *     }
 * }
 * }</pre>
 *
 * <p><b>어떤 이벤트를 듣는지는 발송 주체가 정한다.</b> 위 예시의 {@code MailScheduled}는
 * 발송을 예약한 모듈이 {@code email_log} 행을 만들면서 그 id를 담아 내보내는 <b>자기 모듈
 * 이벤트</b>다. 감사용 이벤트({@code QuoteSent} 등, docs/11-work-breakdown.md §3)를 그대로
 * 듣지 않는 이유는 <b>목적이 다르기 때문</b>이다 — 그쪽은 audit_log 적재를 위해 "무슨 일이
 * 있었나"를 싣지, 발송에 필요한 {@code emailLogId}를 싣지 않는다. 감사 이벤트에서 대상을
 * 역조회하면 재발송 시 같은 견적에 {@code SCHEDULED} 행이 여러 건이라 어느 것인지 가려지지
 * 않는다. 발송 이벤트는 발송 예약과 같은 트랜잭션에서 나와야 id가 정확하다.
 *
 * <p><b>이벤트에는 식별자만 싣는다</b> — {@code emailLogId} 같은 ID다. 엔티티를 실으면
 * 영속성 컨텍스트가 닫힌 뒤 다른 스레드에서 만져 {@code LazyInitializationException}이 나고,
 * 토큰 원문을 실으면 인증 수단이 이벤트 페이로드와 로그를 타고 흐른다
 * (페이로드에 비밀·토큰·해시 금지, docs/11-work-breakdown.md §7.3).
 * 비동기 쪽은 받은 ID로 필요한 값을 <b>새로 조회</b>한다.
 *
 * <h2>거부 정책 — AbortPolicy</h2>
 *
 * <p>{@link ThreadPoolExecutor}의 세 정책은 이렇게 다르다:
 * <ul>
 *   <li>{@link ThreadPoolExecutor.AbortPolicy} — {@code RejectedExecutionException}을 던진다.
 *       작업은 실행되지 않지만 <b>거부 사실이 호출자에게 드러난다</b></li>
 *   <li>{@link ThreadPoolExecutor.DiscardPolicy} — 조용히 버린다. 아무도 모른다</li>
 *   <li>{@link ThreadPoolExecutor.CallerRunsPolicy} — 호출 스레드가 직접 실행한다.
 *       버리지는 않지만 <b>호출자를 그만큼 붙잡는다</b></li>
 * </ul>
 *
 * <p><b>{@code AbortPolicy}를 쓴다</b>(기본값이지만 선택임을 드러내려 명시한다).
 * {@code DiscardPolicy}는 조용한 유실이라 후보가 아니다. {@code CallerRunsPolicy}는
 * 유실은 막지만, 포화 상태에서 메일 발송이 <b>요청 스레드로 되돌아와</b> 응답이 그만큼
 * 지연된다 — 커밋 후라 데이터가 틀어지지는 않아도 사용자가 발송 버튼 앞에서 기다리게 된다.
 * 위의 {@code SCHEDULED} + 배치 구조가 있으면 거부는 유실이 아니라 <b>지연</b>일 뿐이므로,
 * 호출자를 붙잡는 대신 거부를 드러내고 배치에 넘기는 편이 낫다.
 *
 * <h2>그 밖의 결정</h2>
 *
 * <p><b>왜 명시적 풀인가</b> — {@code @EnableAsync}만 켜고 executor를 지정하지 않으면
 * 이름 없는 {@code @Async}가 {@code SimpleAsyncTaskExecutor}로 떨어질 수 있다. 그것은
 * 호출마다 스레드를 새로 만드는 <b>무제한</b> 실행기라, 메일 서버가 느려지는 순간 스레드가
 * 무한히 쌓인다. 그래서 {@link #getAsyncExecutor()}로 기본 실행기 자체를 이 bounded 풀에
 * 못박는다 — 이름을 빠뜨린 {@code @Async}도 여기로 온다.
 *
 * <p><b>Spring Boot 자동 설정과의 관계</b> — {@code TaskExecutionAutoConfiguration}의
 * {@code applicationTaskExecutor}는 {@code @ConditionalOnMissingBean(Executor.class)}라
 * 이 Bean이 생기면 등록되지 않는다. 의도한 결과다: 비동기 대상 실행기가 하나뿐이어야
 * "무제한 풀이 어딘가에 남아 있지 않다"를 테스트로 고정할 수 있다. MVC 비동기
 * ({@code Callable}·{@code DeferredResult} 반환)는 07-api-spec.md에 없어 사용처가 없다 —
 * 생기면 그때 전용 실행기를 별도로 등록한다.
 *
 * <p><b>SecurityContext·요청 스코프를 전파하지 않는다.</b> 커밋 후 발송은 이미 요청이 끝난
 * 뒤에 도는 작업이라, 인증 주체를 끌고 가면 "누구 권한으로 도는지"가 흐려진다. 비동기 작업이
 * 회사·수신자를 알아야 하면 이벤트 페이로드로 받는다 — 페이로드에 비밀·토큰·해시는 금지다
 * (docs/11-work-breakdown.md §7.3).
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncConfig implements AsyncConfigurer {

    /** {@code @Async(AsyncConfig.NOTIFICATION_EXECUTOR)} — D의 메일 리스너가 지정할 이름. */
    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final AsyncProperties properties;

    /** 커밋 후 알림 전용 실행기. 크기는 {@code twojo.async.*} (기본값은 {@link AsyncProperties}). */
    @Bean(name = NOTIFICATION_EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("twojo-async-");
        // 거부를 드러낸다 — 내구성은 email_log SCHEDULED + 재처리 배치가 맡는다 (위 javadoc)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds((int) properties.awaitTermination().toSeconds());
        executor.initialize();
        return executor;
    }

    /** 이름 없는 {@code @Async}도 bounded 풀로 — 무제한 실행기로 흐르는 경로를 남기지 않는다. */
    @Override
    public Executor getAsyncExecutor() {
        return notificationExecutor();
    }

    /**
     * {@code void} 비동기 메서드에서 새어 나온 예외의 최종 처리.
     *
     * <p><b>남기는 것은 어느 메서드가 어떤 예외로 실패했는가뿐이다.</b> 인자도, 예외 메시지도,
     * 스택 트레이스도 찍지 않는다. 이 경로로 오는 값에는 열람 링크 원문 토큰과 수신자 이메일이
     * 섞이는데 — 토큰은 그 자체가 인증 수단이고(ERD: DB엔 해시만 저장), 이메일은 개인정보다 —
     * 그것들이 <b>인자에만 있는 것이 아니다</b>. {@code MailSendException}의 메시지에는 거부된
     * 수신자 주소가, SQL 예외 메시지에는 파라미터가 그대로 들어간다. Spring 기본 핸들러는
     * 인자와 전체 {@code Throwable}을 모두 찍으므로 대체한다.
     *
     * <p><b>그럼 실패 원인은 어디서 보는가</b> — D가 {@code email_log}에 안전한 실패 코드로
     * 기록한다(NT-12). 도메인이 무엇을 안전하다고 판단하는지는 도메인이 정해야 하고,
     * 전역 핸들러는 그 판단을 대신할 수 없다. 여기 오는 것은 D가 잡지 못하고 흘린 예외뿐이므로
     * "어느 리스너가 죽었다"까지만 알리면 된다.
     *
     * <p><b>상관관계 ID는 싣지 않는다</b> — MDC를 비동기 스레드로 전파하지 않기 때문에
     * (요청 스코프 전파 금지, 위 javadoc) 여기서 {@code MDC.get()}은 항상 비어 있다.
     * 비어 있는 값을 형식만 갖춰 찍으면 추적되는 것처럼 보여 더 나쁘다.
     * 발송 단위 추적은 {@code email_log}의 {@code (template_type, ref_id, recipient_email)}로 한다.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return AsyncConfig::logWithoutSensitiveDetail;
    }

    private static void logWithoutSensitiveDetail(Throwable ex, Method method, Object... params) {
        // ex.getMessage()·스택 트레이스는 의도적으로 제외한다 — 예외 메시지에 수신자·토큰이 실린다
        log.error("비동기 실행 실패: {}#{} — {} (인자 {}개, 값·예외 메시지는 민감정보 가능성으로 미기록)",
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                ex.getClass().getName(),
                params.length);
    }
}
