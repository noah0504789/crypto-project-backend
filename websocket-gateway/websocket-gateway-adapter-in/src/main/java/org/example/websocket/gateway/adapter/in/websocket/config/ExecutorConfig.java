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

    // gRPC 응답 콜백이 도는 풀. 지정하지 않으면 gRPC 기본 캐시 풀이라 스레드가 상한 없이 는다.
    // 이 풀만 버리지 않는 이유는 ADR-003 「지키는 것」.
    @Bean("chatMessageAckExecutor")
    public ThreadPoolTaskExecutor chatMessageAckExecutor(MeterRegistry registry, StompExecutorProperties properties) {
        ThreadPoolTaskExecutor executor =
                newExecutor("chat-message-ack-", properties.ack(), new ThreadPoolExecutor.CallerRunsPolicy());

        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "chat.message.ack", Tags.empty());

        return executor;
    }

    // AbortPolicy 를 쓰지 않는 이유는 ADR-003 「지키는 것」. 로그도 남기지 않는다 — 폭주 구간에서
    // 로깅이 다음 병목이 된다. 카운터는 기동 시 미리 만든다. 거절 때마다 빌더를 도는 것도 마찬가지다.
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

    // 채널 하나를 ACK·뱃지·브로드캐스트가 함께 쓴다. 무엇이 버려졌는지 갈라 보려고 목적지로 분류한다
    // (피해가 다르다 — SERVICE_FLOWS.md §15). MessageHandlingRunnable 은 원본 메시지를 꺼내는 공개 API 다.
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

    // core == max 라 유휴 타임아웃은 스레드를 죽였다 다시 만들기만 한다(이전 측정에서 30초에 919건 생성).
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
