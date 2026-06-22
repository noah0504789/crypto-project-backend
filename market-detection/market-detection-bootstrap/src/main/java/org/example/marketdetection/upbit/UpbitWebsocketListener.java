package org.example.marketdetection.upbit;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.example.common.clock.Clock;
import org.example.common.event.KafkaEvent;
import org.example.contract.market.MarketResponse;
import org.example.market.client.MarketClient;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitWebsocketListener extends WebSocketListener {

    private final UpbitWebsocketService websocketService;
    private final UpbitProperties properties;
    private final Clock clock;
    private final MarketClient marketClient;
    private ConcurrentMap<String, AtomicLong> tickerLastSentByCode;
    private BlockingQueue<KafkaEvent> tickerQueue;

    @PostConstruct
    void init() {
        this.tickerLastSentByCode = new ConcurrentHashMap<>();
        this.tickerQueue = new LinkedBlockingQueue<>(properties.websocket().tickerQueueCapacity());
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        super.onOpen(webSocket, response);

        websocketService.subscribe(webSocket, resolveSubscribeCodes());
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
        super.onMessage(webSocket, bytes);

        KafkaEvent event = websocketService.deserialize(bytes);

        if (!(event instanceof UpbitTickerEvent tickerEvent) || !tryUpdateTickerLastSent(tickerEvent)) {
            return;
        }

        offerTickerQueue(tickerEvent);
    }

    public KafkaEvent pollTickerQueue() {
        return tickerQueue.poll();
    }

    private List<String> resolveSubscribeCodes() {
        List<String> subscribeCodes = marketClient.getEnabledMarkets()
                .stream()
                .map(MarketResponse::marketCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();

        if (subscribeCodes.isEmpty()) {
            throw new IllegalStateException("No enabled markets found for Upbit websocket subscription.");
        }

        log.info("resolved upbit subscribe codes. codes={}", subscribeCodes);

        return subscribeCodes;
    }

    private boolean tryUpdateTickerLastSent(UpbitTickerEvent tickerEvent) {
        String code = tickerEvent.code();

        if (code == null || code.isBlank()) {
            log.warn("ticker event code is blank. event={}", tickerEvent);
            return false;
        }

        long now = clock.nowMs();

        AtomicLong lastSent = tickerLastSentByCode.computeIfAbsent(code, ignored -> new AtomicLong(0));
        long previous = lastSent.get();
        long intervalMillis = properties.websocket().tickerPublishInterval().toMillis();

        if (now - previous < intervalMillis) {
            return false;
        }

        return lastSent.compareAndSet(previous, now);
    }

    private void offerTickerQueue(KafkaEvent event) {
        boolean offered = tickerQueue.offer(event);

        if (!offered) {
            log.warn("ticker queue is full. droppedEvent={}", event.getClass().getSimpleName());
        }
    }
}