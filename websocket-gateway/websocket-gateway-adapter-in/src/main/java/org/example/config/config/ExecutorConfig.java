package org.example.config.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

    @Bean("chatSaveExecutor")
    public ThreadPoolTaskExecutor chatSaveExecutor(MeterRegistry registry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("chat-save-");
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();

//        ExecutorServiceMetrics.monitor(registry, executor.getThreadPoolExecutor(), "stomp.broker", Tags.empty());

        return executor;
    }
}
