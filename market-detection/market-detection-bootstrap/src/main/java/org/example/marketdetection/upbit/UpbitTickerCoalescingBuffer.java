package org.example.marketdetection.upbit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.springframework.stereotype.Component;

@Component
public class UpbitTickerCoalescingBuffer {

    private final ConcurrentMap<String, TickerSlot> latestTickerByCode = new ConcurrentHashMap<>();
    private final BlockingQueue<String> readyQueue;
    private final Counter coalescedTickerCounter;
    private final Counter readyQueueOfferFailureCounter;

    public UpbitTickerCoalescingBuffer(UpbitProperties properties, MeterRegistry meterRegistry) {
        this.readyQueue =
                new LinkedBlockingQueue<>(properties.websocket().tickerReadyQueueCapacity());
        this.coalescedTickerCounter =
                meterRegistry.counter("market_detection_ticker_coalesced_total");
        this.readyQueueOfferFailureCounter =
                meterRegistry.counter("market_detection_ticker_ready_queue_offer_failures_total");

        Gauge.builder("market_detection_ticker_ready_queue_size", readyQueue, BlockingQueue::size)
                .register(meterRegistry);
    }

    public boolean offer(UpbitTickerEvent tickerEvent) {
        String code = tickerEvent.code();
        TickerSlot slot = latestTickerByCode.computeIfAbsent(code, ignored -> new TickerSlot());

        synchronized (slot) {
            slot.latestTicker = tickerEvent;
            slot.version++;

            if (slot.state != TickerState.IDLE) {
                coalescedTickerCounter.increment();
                return true;
            }

            slot.state = TickerState.QUEUED;

            if (readyQueue.offer(code)) {
                return true;
            }

            slot.state = TickerState.IDLE;
            readyQueueOfferFailureCounter.increment();
            return false;
        }
    }

    public TickerTask take() throws InterruptedException {
        while (true) {
            String code = readyQueue.take();
            TickerSlot slot = latestTickerByCode.get(code);

            if (slot == null) {
                continue;
            }

            synchronized (slot) {
                if (slot.state != TickerState.QUEUED || slot.latestTicker == null) {
                    continue;
                }

                slot.state = TickerState.PROCESSING;
                return new TickerTask(code, slot.latestTicker, slot.version);
            }
        }
    }

    public void complete(TickerTask task) {
        TickerSlot slot = latestTickerByCode.get(task.code());

        if (slot == null) {
            return;
        }

        synchronized (slot) {
            if (slot.state != TickerState.PROCESSING) {
                return;
            }

            if (slot.version == task.version()) {
                slot.state = TickerState.IDLE;
                return;
            }

            slot.state = TickerState.QUEUED;

            if (!readyQueue.offer(task.code())) {
                slot.state = TickerState.IDLE;
                readyQueueOfferFailureCounter.increment();
            }
        }
    }

    int readyQueueSize() {
        return readyQueue.size();
    }

    UpbitTickerEvent latestTicker(String code) {
        TickerSlot slot = latestTickerByCode.get(code);

        if (slot == null) {
            return null;
        }

        synchronized (slot) {
            return slot.latestTicker;
        }
    }

    public record TickerTask(String code, UpbitTickerEvent tickerEvent, long version) {}

    private static final class TickerSlot {
        private UpbitTickerEvent latestTicker;
        private long version;
        private TickerState state = TickerState.IDLE;
    }

    private enum TickerState {
        IDLE,
        QUEUED,
        PROCESSING
    }
}
