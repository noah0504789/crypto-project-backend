package org.example.marketdetection.infra.config;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TickerWorkerConfig {

    @Bean
    public ThreadPoolTaskExecutor upbitTickerWorkerExecutor(UpbitProperties properties) {
        int workerCount = properties.websocket().tickerWorkerCount();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("upbit-ticker-worker-");
        executor.setCorePoolSize(workerCount);
        executor.setMaxPoolSize(workerCount);
        executor.setQueueCapacity(0);
        executor.setWaitForTasksToCompleteOnShutdown(false);

        return executor;
    }

    @Bean
    public MeterBinder upbitTickerWorkerExecutorMetrics(
            @Qualifier("upbitTickerWorkerExecutor") ThreadPoolTaskExecutor executor) {
        return registry ->
                ExecutorServiceMetrics.monitor(
                        registry,
                        executor.getThreadPoolExecutor(),
                        "market_detection_ticker_worker",
                        Tags.empty());
    }
}
