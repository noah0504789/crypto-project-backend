package org.example.marketdetection.upbit;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.example.common.event.KafkaEvent;
import org.example.common.time.Clock;
import org.example.contract.market.MarketResponse;
import org.example.market.client.MarketClient;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitWebsocketListener extends WebSocketListener {

    private final UpbitWebsocketService websocketService;
    private final UpbitProperties properties;
    private final Clock clock;
    private final MarketClient marketClient;
    private final UpbitTickerCoalescingBuffer tickerBuffer;
    private ConcurrentMap<String, AtomicLong> tickerLastSentByCode;

    @PostConstruct
    void init() {
        this.tickerLastSentByCode = new ConcurrentHashMap<>();
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

        if (!(event instanceof UpbitTickerEvent tickerEvent)) {
            return;
        }

        offerTicker(tickerEvent);
    }

    private List<String> resolveSubscribeCodes() {
        List<String> subscribeCodes =
                marketClient.getEnabledMarkets().stream()
                        .map(MarketResponse::marketCode)
                        .filter(code -> code != null && !code.isBlank())
                        .distinct()
                        .toList();

        if (subscribeCodes.isEmpty()) {
            throw new IllegalStateException(
                    "No enabled markets found for Upbit websocket subscription.");
        }

        log.info("[upbit] resolved upbit subscribe codes. codes={}", subscribeCodes);

        return subscribeCodes;
    }

    private void offerTicker(UpbitTickerEvent tickerEvent) {
        String code = tickerEvent.code();

        if (code == null || code.isBlank()) {
            log.warn("[upbit] ticker event code is blank. event={}", tickerEvent);
            return;
        }

        long now = clock.nowMs();

        AtomicLong lastSent =
                tickerLastSentByCode.computeIfAbsent(code, ignored -> new AtomicLong(0));
        long previous = lastSent.get();
        long intervalMillis = properties.websocket().tickerPublishInterval().toMillis();

        if (now - previous < intervalMillis) {
            return;
        }

        if (!lastSent.compareAndSet(previous, now)) {
            return;
        }

        if (!tickerBuffer.offer(tickerEvent)) {
            lastSent.compareAndSet(now, previous);
            log.warn("[upbit] ticker ready queue is full. code={}", code);
        }
    }
}
