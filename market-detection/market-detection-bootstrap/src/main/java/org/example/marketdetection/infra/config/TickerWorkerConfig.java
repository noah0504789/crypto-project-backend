package org.example.marketdetection.infra.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TickerWorkerConfig {

    @Bean
    public ThreadPoolTaskExecutor upbitTickerWorkerExecutor(
            UpbitProperties properties, MeterRegistry meterRegistry) {
        int workerCount = properties.websocket().tickerWorkerCount();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("upbit-ticker-worker-");
        executor.setCorePoolSize(workerCount);
        executor.setMaxPoolSize(workerCount);
        executor.setQueueCapacity(0);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();

        ExecutorServiceMetrics.monitor(
                meterRegistry,
                executor.getThreadPoolExecutor(),
                "market_detection_ticker_worker",
                Tags.empty());

        return executor;
    }
}
