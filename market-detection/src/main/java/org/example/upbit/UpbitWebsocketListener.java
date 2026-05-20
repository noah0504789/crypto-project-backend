package org.example.upbit;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.example.common.clock.Clock;
import org.example.common.event.KafkaEvent;
import org.example.infra.properties.UpbitProperties;
import org.example.upbit.event.UpbitTickerEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitWebsocketListener extends WebSocketListener {

    private final UpbitWebsocketService websocketService;
    private final UpbitProperties properties;
    private final Clock clock;

    private final AtomicLong tickerLastSent = new AtomicLong(0);
    private BlockingQueue<KafkaEvent> tickerQueue;

    @PostConstruct
    void init() {
        int capacity = properties.websocket().tickerQueueCapacity();

        this.tickerQueue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        super.onOpen(webSocket, response);

        websocketService.subscribe(
                webSocket,
                properties.websocket().subscribeCodes()
        );
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
        super.onMessage(webSocket, bytes);

        KafkaEvent event = websocketService.deserialize(bytes);

        if (!(event instanceof UpbitTickerEvent)) {
            return;
        }

        if (!tryUpdateTickerLastSent()) {
            return;
        }

        offerTickerQueue(event);
    }

    public KafkaEvent pollTickerQueue() {
        return tickerQueue.poll();
    }

    private boolean tryUpdateTickerLastSent() {
        long now = clock.nowMs();
        long lastSent = tickerLastSent.get();

        if (now - lastSent < properties.websocket().tickerPublishInterval().toMillis()) {
            return false;
        }

        return tickerLastSent.compareAndSet(lastSent, now);
    }

    private void offerTickerQueue(KafkaEvent event) {
        boolean offered = tickerQueue.offer(event);

        if (offered) {
            return;
        }

        log.warn("ticker queue is full. droppedEvent={}", event.getClass().getSimpleName());
    }
}