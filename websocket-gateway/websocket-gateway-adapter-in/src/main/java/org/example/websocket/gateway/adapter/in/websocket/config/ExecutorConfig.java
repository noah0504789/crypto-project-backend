package org.example.websocket.gateway.adapter.in.websocket.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
public class ExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor stompInboundExecutor(MeterRegistry registry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("stomp-in-");
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.initialize();

        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "stomp.inbound", Tags.empty());

        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor stompBrokerExecutor(MeterRegistry registry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("stomp-broker-");
        executor.setCorePoolSize(32);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(250);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();

        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "stomp.broker", Tags.empty());

        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor stompOutboundExecutor(MeterRegistry registry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("stomp-out-");
        executor.setCorePoolSize(48);
        executor.setMaxPoolSize(96);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();

        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "stomp.outbound", Tags.empty());

        return executor;
    }

    // gRPC save 응답 콜백(ACK 전송)이 도는 풀. 지정하지 않으면 gRPC 기본 캐시 풀에서 돌아
    // 부하 시 스레드가 상한 없이 늘어난다. 큐가 차면 호출 스레드가 직접 처리해 ACK를 버리지 않는다.
    @Bean("chatMessageAckExecutor")
    public ThreadPoolTaskExecutor chatMessageAckExecutor(MeterRegistry registry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("chat-message-ack-");
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "chat.message.ack", Tags.empty());

        return executor;
    }
}
