package org.example.upbit;

import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.example.infra.properties.UpbitProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpbitWebsocketClientStarter {

    private final OkHttpClient okHttpClient;
    private final UpbitWebsocketListener listener;
    private final UpbitProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Request request = new Request.Builder()
                .url(properties.websocket().url())
                .build();

        okHttpClient.newWebSocket(request, listener);
    }
}