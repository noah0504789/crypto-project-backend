package org.example.websocket.gateway.adapter.in.websocket.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.example.common.enums.StompDestination;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageHandlingRunnable;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
public class ExecutorConfig {

    private static final List<String> REJECTED_KINDS =
            List.of("broadcast", "ack", "badge", "notification", "other", "none", "unknown");

    @Bean
    public ThreadPoolTaskExecutor stompInboundExecutor(MeterRegistry registry, StompExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = newExecutor("stomp-in-", properties.inbound(), sheddingHandler(registry, "inbound"));

        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "stomp.inbound", Tags.empty());

        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor stompBrokerExecutor(MeterRegistry registry, StompExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = newExecutor("stomp-broker-", properties.broker(), sheddingHandler(registry, "broker"));

        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "stomp.broker", Tags.empty());

        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor stompOutboundExecutor(MeterRegistry registry, StompExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = newExecutor("stomp-out-", properties.outbound(), sheddingHandler(registry, "outbound"));

        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "stomp.outbound", Tags.empty());

        return executor;
    }

    // gRPC save 응답 콜백(ACK 전송)이 도는 풀. 지정하지 않으면 gRPC 기본 캐시 풀에서 돌아
    // 부하 시 스레드가 상한 없이 늘어난다. 큐가 차면 호출 스레드가 직접 처리해 ACK를 버리지 않는다.
    // 팬아웃 풀과 달리 shedding 하지 않는다 — ACK를 버리면 발신자가 전송 성공 여부를 알 수 없고,
    // ACK 발생량은 사용자 수에 선형(팬아웃처럼 제곱이 아님)이라 CallerRuns 배압이 오래 가지 않는다.
    @Bean("chatMessageAckExecutor")
    public ThreadPoolTaskExecutor chatMessageAckExecutor(MeterRegistry registry, StompExecutorProperties properties) {
        ThreadPoolTaskExecutor executor =
                newExecutor("chat-message-ack-", properties.ack(), new ThreadPoolExecutor.CallerRunsPolicy());

        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "chat.message.ack", Tags.empty());

        return executor;
    }

    // 기본 AbortPolicy 를 쓰지 않는다. AbortPolicy 는 예외 메시지를 만들며 ThreadPoolExecutor.toString() 을
    // 호출하고, 그 안에서 mainLock 을 잡는다. 정상 제출 경로는 이 락을 쓰지 않고 거부 경로만 쓰므로,
    // 거부가 폭주하면 제출 스레드가 전부 이 락에서 직렬화되고 느려진 제출이 큐를 더 밀어 거부를 늘린다.
    // JFR 30초 녹화에서 락 대기 3,637건 중 3,613건이 이 경로였다.
    // 로그가 아니라 카운터를 남긴다 — 폭주 구간에서는 로깅 자체가 다음 병목이 된다.
    // 카운터를 미리 만들어 둔다. 거절 폭주 구간에서 매번 빌더를 도는 것 자체가 다음 병목이 된다.
    private RejectedExecutionHandler sheddingHandler(MeterRegistry registry, String poolName) {
        Map<String, Counter> counters = new HashMap<>();

        for (String kind : REJECTED_KINDS) {
            counters.put(kind, Counter.builder("stomp.executor.rejected")
                    .description("큐 포화로 버려진 STOMP 태스크 수")
                    .tag("pool", poolName)
                    .tag("kind", kind)
                    .register(registry));
        }

        return (task, executor) -> counters.get(classify(task)).increment();
    }

    // 채널 하나를 ACK·뱃지·브로드캐스트가 함께 쓰므로 거절 수만으로는 무엇이 사라졌는지 알 수 없다.
    // 피해가 다르다 — 브로드캐스트 1건은 방 전원, ACK 1건은 발신자가 결과를 영영 모른다.
    // 태스크는 SendTask 이고 MessageHandlingRunnable 로 원본 메시지를 꺼낼 수 있다(공개 API).
    private String classify(Runnable task) {
        if (!(task instanceof MessageHandlingRunnable runnable)) {
            return "unknown";
        }

        Object destination = runnable.getMessage().getHeaders().get(SimpMessageHeaderAccessor.DESTINATION_HEADER);

        if (!(destination instanceof String value)) {
            return "none";
        }

        if (value.startsWith(StompDestination.CHAT_ROOM_PREFIX.destination())) {
            return "broadcast";
        }

        // 사용자 목적지는 브로커가 세션별로 다시 쓰므로 접미사가 붙는다. contains 로 본다.
        if (value.contains(StompDestination.CHAT_ACK_QUEUE.destination())) {
            return "ack";
        }

        if (value.contains(StompDestination.CHAT_ROOM_BADGE_QUEUE.destination())) {
            return "badge";
        }

        if (value.startsWith(StompDestination.NOTIFICATION_PREFIX.destination())) {
            return "notification";
        }

        return "other";
    }

    // allowCoreThreadTimeOut(false): core == max 운용이라 유휴 타임아웃은 스레드를 죽였다 다시 만들기만 한다.
    // 이전 측정에서 거부 급증 구간의 스레드 생성이 30초에 919건까지 올랐다. keepAlive 는 같은 이유로 두지 않는다.
    private ThreadPoolTaskExecutor newExecutor(
            String threadNamePrefix,
            StompExecutorProperties.Pool pool,
            RejectedExecutionHandler rejectedExecutionHandler
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(pool.coreSize());
        executor.setMaxPoolSize(pool.maxSize());
        executor.setQueueCapacity(pool.queueCapacity());
        executor.setAllowCoreThreadTimeOut(false);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler);
        executor.initialize();

        return executor;
    }
}
