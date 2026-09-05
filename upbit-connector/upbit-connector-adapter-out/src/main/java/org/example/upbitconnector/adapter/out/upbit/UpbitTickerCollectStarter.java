package org.example.upbitconnector.adapter.out.upbit;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.upbitconnector.adapter.out.metrics.UpbitMetricNames;
import org.example.upbitconnector.application.properties.UpbitProperties;
import org.example.upbitconnector.application.service.UpbitTickerCollectService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "upbit.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UpbitTickerCollectStarter implements ApplicationRunner {

    private final UpbitTickerCollectService collectService;
    private final UpbitProperties properties;
    private final MeterRegistry meterRegistry;

    private volatile Disposable subscription;

    @Override
    public void run(ApplicationArguments args) {
        subscription = collectContinuously()
                .subscribe(null, this::logPermanentTermination);

        log.info("[upbit] ticker collect started.");
    }

    private Mono<Void> collectContinuously() {
        UpbitProperties.Websocket websocket = properties.websocket();

        return Mono.defer(collectService::collect)
                .doOnSuccess(ignored -> logStreamCompleted())
                .repeatWhen(completed -> completed.delayElements(websocket.reconnectMinBackoff()))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, websocket.reconnectMinBackoff())
                        .maxBackoff(websocket.reconnectMaxBackoff())
                        .jitter(0.5)
                        .doBeforeRetry(signal -> logRetry(signal.totalRetries() + 1, signal.failure())))
                .then();
    }

    @PreDestroy
    public void stop() {
        if (subscription == null) {
            return;
        }

        subscription.dispose();
        subscription = null;

        log.info("[upbit] ticker collect stopped.");
    }

    private void logStreamCompleted() {
        countRestart(UpbitMetricNames.REASON_COMPLETED);

        log.warn("[upbit] ticker stream completed. restarting.");
    }

    private void logRetry(long attempts, Throwable error) {
        countRestart(UpbitMetricNames.REASON_ERROR);

        log.warn("[upbit] ticker collect retry. attempts={}", attempts, error);
    }

    // WebSocket 어댑터의 재연결 바깥에서 파이프라인이 통째로 다시 조립되는 횟수다.
    private void countRestart(String reason) {
        meterRegistry.counter(UpbitMetricNames.COLLECT_RESTART, UpbitMetricNames.TAG_REASON, reason).increment();
    }

    private void logPermanentTermination(Throwable error) {
        log.error("[upbit] ticker collect permanently terminated.", error);
    }
}
