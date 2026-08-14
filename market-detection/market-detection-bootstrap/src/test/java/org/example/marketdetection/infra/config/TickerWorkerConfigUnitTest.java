package org.example.marketdetection.infra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TickerWorkerConfigUnitTest {

    @Test
    @DisplayName("Spring 초기화 이후 실제 ticker worker executor에 metric을 등록한다")
    void workerExecutor_afterSpringInitialization_registerMetricsOnActualExecutor() {
        // given
        TickerWorkerConfig sut = new TickerWorkerConfig();
        ThreadPoolTaskExecutor executor = sut.upbitTickerWorkerExecutor(createProperties(2));

        assertThatIllegalStateException().isThrownBy(executor::getThreadPoolExecutor);

        executor.afterPropertiesSet();
        MeterBinder meterBinder = sut.upbitTickerWorkerExecutorMetrics(executor);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        try {
            // when
            meterBinder.bindTo(meterRegistry);

            // then
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
            assertThat(
                            meterRegistry
                                    .get("executor.pool.core")
                                    .tag("name", "market_detection_ticker_worker")
                                    .gauge()
                                    .value())
                    .isEqualTo(2.0);
        } finally {
            executor.destroy();
            meterRegistry.close();
        }
    }

    private UpbitProperties createProperties(int workerCount) {
        return new UpbitProperties(
                new UpbitProperties.Websocket(
                        "wss://api.upbit.com/websocket/v1",
                        "test",
                        Duration.ZERO,
                        100,
                        workerCount),
                new UpbitProperties.Ticker(
                        new UpbitProperties.Ticker.Alert(3, Duration.ofSeconds(10))),
                new UpbitProperties.Store(
                        new UpbitProperties.Store.StoreTicker(
                                "upbit-ticker-store",
                                Duration.ofMinutes(3),
                                Duration.ofMinutes(3),
                                false)));
    }
}
