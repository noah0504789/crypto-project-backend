package org.example.marketdetection.upbit;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import okhttp3.*;
import okio.ByteString;
import org.example.common.event.KafkaEvent;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.example.common.test.config.TestBootApplication;
import config.TestPropertiesConfig;
import config.TestUpbitExternalDependencyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("external")
@SpringBootTest(classes = {
        TestBootApplication.class,

        // 실제 테스트 대상
        UpbitWebsocketService.class,

        // 테스트 설정
        TestPropertiesConfig.class,
        TestUpbitExternalDependencyConfig.class
})
class UpbitWebsocketServiceExternalIntegrationTest {

    private static final List<String> SUBSCRIBE_CODES = List.of("KRW-BTC");

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private UpbitWebsocketService websocketService;

    @Autowired
    private UpbitProperties properties;

    @Disabled
    @Test
    @DisplayName("실제 Upbit WebSocket에 연결하면 ticker 메시지를 수신한다")
    void connectUpbitWebsocket_receiveTickerMessage() throws Exception {
        // given
        CountDownLatch messageLatch = new CountDownLatch(1);

        AtomicReference<KafkaEvent> receivedEvent = new AtomicReference<>();
        AtomicReference<String> rawMessage = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean receivedSuccessfully = new AtomicBoolean(false);

        WebSocketListener listener = new WebSocketListener() {

            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                websocketService.subscribe(webSocket, SUBSCRIBE_CODES);
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
        };

        Request request = new Request.Builder()
                .url(properties.websocket().url())
                .build();

        // when
        WebSocket webSocket = okHttpClient.newWebSocket(request, listener);
        boolean received = messageLatch.await(10, TimeUnit.SECONDS);

        if (!receivedSuccessfully.get()) {
            webSocket.cancel();
        }

        // then
        assertThat(received)
                .as("10초 안에 Upbit WebSocket 메시지를 수신해야 한다")
                .isTrue();

        assertThat(failure.get())
                .as("메시지 수신 전 WebSocket 연결 또는 역직렬화 실패가 없어야 한다")
                .isNull();

        assertThat(rawMessage.get())
                .as("원본 메시지가 존재해야 한다")
                .isNotBlank();

        assertThat(receivedEvent.get())
                .as("수신 메시지가 KafkaEvent로 변환되어야 한다")
                .isInstanceOf(UpbitTickerEvent.class);

        UpbitTickerEvent tickerEvent = (UpbitTickerEvent) receivedEvent.get();

        assertThat(tickerEvent.type()).isEqualTo("ticker");
        assertThat(tickerEvent.code()).isIn(SUBSCRIBE_CODES);
        assertThat(tickerEvent.tradePrice()).isNotNull();
        assertThat(tickerEvent.getPartitionKey()).isEqualTo(tickerEvent.code());
    }
}