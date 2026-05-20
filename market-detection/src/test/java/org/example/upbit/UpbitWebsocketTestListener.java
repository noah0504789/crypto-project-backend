package org.example.upbit;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.example.common.event.KafkaEvent;
import org.example.infra.properties.UpbitProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class UpbitWebsocketTestListener extends WebSocketListener {

    private final UpbitWebsocketService websocketService;
    private final UpbitProperties properties;
    private final CountDownLatch messageLatch;

    private final AtomicReference<KafkaEvent> receivedEvent = new AtomicReference<>();
    private final AtomicReference<String> rawMessage = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicBoolean receivedSuccessfully = new AtomicBoolean(false);

    public UpbitWebsocketTestListener(
            UpbitWebsocketService websocketService,
            UpbitProperties properties,
            CountDownLatch messageLatch
    ) {
        this.websocketService = websocketService;
        this.properties = properties;
        this.messageLatch = messageLatch;
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        websocketService.subscribe(
                webSocket,
                properties.websocket().subscribeCodes()
        );
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
        rawMessage.set(bytes.utf8());

        try {
            KafkaEvent event = websocketService.deserialize(bytes);
            receivedEvent.set(event);
            receivedSuccessfully.set(true);
        } catch (Throwable t) {
            failure.set(t);
        } finally {
            messageLatch.countDown();
            webSocket.close(1000, "test completed");
        }
    }

    @Override
    public void onFailure(
            @NotNull WebSocket webSocket,
            @NotNull Throwable t,
            @Nullable Response response
    ) {
        if (receivedSuccessfully.get()) {
            return;
        }

        failure.set(t);
        messageLatch.countDown();
    }

    public KafkaEvent receivedEvent() {
        return receivedEvent.get();
    }

    public String rawMessage() {
        return rawMessage.get();
    }

    public Throwable failure() {
        return failure.get();
    }

    public boolean receivedSuccessfully() {
        return receivedSuccessfully.get();
    }
}