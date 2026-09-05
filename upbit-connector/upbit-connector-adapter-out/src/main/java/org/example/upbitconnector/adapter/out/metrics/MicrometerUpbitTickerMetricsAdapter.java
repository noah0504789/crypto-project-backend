package org.example.upbitconnector.adapter.out.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.example.upbitconnector.application.port.out.UpbitTickerMetricsPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicrometerUpbitTickerMetricsAdapter implements UpbitTickerMetricsPort {

    private final MeterRegistry meterRegistry;

    // 종목당 초당 1건 미만이라 경합은 없지만, 매 건 빌더를 도는 대신 종목별로 한 번만 만든다.
    private final Map<String, Counter> receivedCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> publishedCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> publishFailedCounters = new ConcurrentHashMap<>();

    @Override
    public void tickerReceived(String code) {
        receivedCounters.computeIfAbsent(code, this::newReceivedCounter).increment();
    }

    @Override
    public void tickerPublished(String code, long elapsedNanos) {
        publishedCounters.computeIfAbsent(code, this::newPublishedCounter).increment();
        publishLatency().record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void tickerPublishFailed(String code) {
        publishFailedCounters.computeIfAbsent(code, this::newPublishFailedCounter).increment();
    }

    private Counter newReceivedCounter(String code) {
        return Counter.builder(UpbitMetricNames.TICKER_RECEIVED)
                .description("Upbit WebSocket 에서 받은 ticker 수(스로틀 이전)")
                .tag(UpbitMetricNames.TAG_CODE, code)
                .register(meterRegistry);
    }

    private Counter newPublishedCounter(String code) {
        return Counter.builder(UpbitMetricNames.TICKER_PUBLISHED)
                .description("upbit-ticker-event 로 발행에 성공한 ticker 수")
                .tag(UpbitMetricNames.TAG_CODE, code)
                .register(meterRegistry);
    }

    private Counter newPublishFailedCounter(String code) {
        return Counter.builder(UpbitMetricNames.TICKER_PUBLISH_FAILED)
                .description("발행에 실패해 건너뛴 ticker 수")
                .tag(UpbitMetricNames.TAG_CODE, code)
                .register(meterRegistry);
    }

    // 발행 지연은 Kafka 쪽 성질이라 종목으로 가르지 않는다.
    private Timer publishLatency() {
        return Timer.builder(UpbitMetricNames.TICKER_PUBLISH_LATENCY)
                .description("StreamBridge 발행 호출에 걸린 시간")
                .register(meterRegistry);
    }
}
