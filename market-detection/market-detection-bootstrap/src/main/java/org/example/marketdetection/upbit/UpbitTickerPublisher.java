package org.example.marketdetection.upbit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.example.common.event.KafkaEventFactory;
import org.example.common.time.Clock;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.UpbitTickerCoalescingBuffer.TickerTask;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.Message;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UpbitTickerPublisher implements SmartLifecycle {

    static final String OUTPUT_BINDING = "upbitTickerEvent-out-0";

    private final UpbitTickerCoalescingBuffer tickerBuffer;
    private final StreamBridge streamBridge;
    private final ThreadPoolTaskExecutor workerExecutor;
    private final int workerCount;
    private final Counter processedTickerCounter;
    private final Counter workerErrorCounter;
    private final Timer processingTimer;
    private final Clock clock;
    private final List<Future<?>> workerFutures = new ArrayList<>();
    private volatile boolean running;

    public UpbitTickerPublisher(
            UpbitTickerCoalescingBuffer tickerBuffer,
            StreamBridge streamBridge,
            @Qualifier("upbitTickerWorkerExecutor") ThreadPoolTaskExecutor workerExecutor,
            UpbitProperties properties,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.tickerBuffer = tickerBuffer;
        this.streamBridge = streamBridge;
        this.workerExecutor = workerExecutor;
        this.workerCount = properties.websocket().tickerWorkerCount();
        this.processedTickerCounter =
                meterRegistry.counter("market_detection_ticker_processed_total");
        this.workerErrorCounter =
                meterRegistry.counter("market_detection_ticker_worker_errors_total");
        this.processingTimer = meterRegistry.timer("market_detection_ticker_processing");
        this.clock = clock;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        try {
            for (int index = 0; index < workerCount; index++) {
                workerFutures.add(workerExecutor.submit(this::runWorker));
            }
        } catch (RuntimeException e) {
            interruptWorkers();
            throw e;
        }

        running = true;
        log.info("[upbit] ticker publisher workers started. workerCount={}", workerCount);
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        interruptWorkers();
        running = false;
        log.info("[upbit] ticker publisher workers interrupted for shutdown.");
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TickerTask task = tickerBuffer.take();

                try {
                    publish(task);
                    processedTickerCounter.increment();
                } catch (RuntimeException e) {
                    workerErrorCounter.increment();
                    log.error("[upbit] failed to publish ticker event. code={}", task.code(), e);
                } finally {
                    tickerBuffer.complete(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void publish(TickerTask task) {
        long startedAt = clock.monotonicTimeNanos();
        Message<?> message =
                KafkaEventFactory.createEventMessage(
                        task.tickerEvent(), task.code(), task.tickerEvent().getClass().getName());

        try {
            if (!streamBridge.send(OUTPUT_BINDING, message)) {
                throw new IllegalStateException("Upbit ticker publish failed. code=" + task.code());
            }
        } finally {
            processingTimer.record(clock.monotonicTimeNanos() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    private void interruptWorkers() {
        workerFutures.forEach(future -> future.cancel(true));
        workerFutures.clear();
    }
}
