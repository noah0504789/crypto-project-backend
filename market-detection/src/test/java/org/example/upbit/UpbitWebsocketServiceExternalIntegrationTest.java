package org.example.upbit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import org.example.infra.properties.UpbitProperties;
import org.example.upbit.event.UpbitTickerEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private UpbitWebsocketService websocketService;

    @Autowired
    private UpbitProperties properties;

    @Test
    @DisplayName("실제 Upbit WebSocket에 연결하면 ticker 메시지를 수신한다")
    void connectUpbitWebsocket_receiveTickerMessage() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(1);

        UpbitWebsocketTestListener listener = new UpbitWebsocketTestListener(
                websocketService,
                properties,
                messageLatch
        );

        Request request = new Request.Builder()
                .url(properties.websocket().url())
                .build();

        WebSocket webSocket = okHttpClient.newWebSocket(request, listener);

        boolean received = messageLatch.await(10, TimeUnit.SECONDS);

        if (!listener.receivedSuccessfully()) {
            webSocket.cancel();
        }

        assertThat(received)
                .as("10초 안에 Upbit WebSocket 메시지를 수신해야 한다")
                .isTrue();

        assertThat(listener.failure())
                .as("메시지 수신 전 WebSocket 연결 또는 역직렬화 실패가 없어야 한다")
                .isNull();

        assertThat(listener.rawMessage())
                .as("원본 메시지가 존재해야 한다")
                .isNotBlank();

        assertThat(listener.receivedEvent())
                .as("수신 메시지가 KafkaEvent로 변환되어야 한다")
                .isInstanceOf(UpbitTickerEvent.class);

        UpbitTickerEvent tickerEvent = (UpbitTickerEvent) listener.receivedEvent();

        assertThat(tickerEvent.type()).isEqualTo("ticker");
        assertThat(tickerEvent.code()).isIn(properties.websocket().subscribeCodes());
        assertThat(tickerEvent.tradePrice()).isNotNull();
        assertThat(tickerEvent.getPartitionKey()).isEqualTo(tickerEvent.code());
    }
}