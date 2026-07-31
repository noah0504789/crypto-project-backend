package org.example.marketdetection.upbit;

import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import org.example.common.clock.Clock;
import org.example.common.event.KafkaEvent;
import org.example.contract.market.MarketResponse;
import org.example.market.client.MarketClient;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpbitWebsocketListenerTest {

    private static final String CODE = "KRW-BTC";
    private static final String OTHER_CODE = "KRW-ETH";

    @Mock
    private UpbitWebsocketService websocketService;

    @Mock
    private Clock clock;

    @Mock
    private MarketClient marketClient;

    @Mock
    private WebSocket webSocket;

    @Mock
    private Response response;

    private UpbitWebsocketListener sut;

    @BeforeEach
    void setUp() {
        sut = createSut(createProperties(Duration.ofSeconds(3), 100));
    }

    @Test
    @DisplayName("웹소켓이 열리면 enabled market 코드로 구독을 요청한다")
    void onOpen_subscribeEnabledMarkets() {
        // given
        given(marketClient.getEnabledMarkets()).willReturn(List.of(
                marketResponse()
        ));

        // when
        sut.onOpen(webSocket, response);

        // then
        verify(websocketService).subscribe(
                webSocket,
                List.of(CODE)
        );
    }

    @Test
    @DisplayName("enabled market이 없으면 예외가 발생한다")
    void onOpen_enabledMarketsEmpty_throwException() {
        // given
        given(marketClient.getEnabledMarkets()).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> sut.onOpen(webSocket, response))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ticker 이벤트이면 큐에 적재한다")
    void onMessage_tickerEvent_offerQueue() {
        // given
        ByteString bytes = ByteString.encodeUtf8("{}");
        UpbitTickerEvent event = tickerEvent(CODE, 100.0);

        given(websocketService.deserialize(bytes)).willReturn(event);
        given(clock.nowMs()).willReturn(10_000L);

        // when
        sut.onMessage(webSocket, bytes);

        // then
        KafkaEvent polled = sut.pollTickerQueue();

        assertThat(polled).isSameAs(event);
    }

    @Test
    @DisplayName("ticker 이벤트가 아니면 큐에 적재하지 않는다")
    void onMessage_notTickerEvent_ignore() {
        // given
        ByteString bytes = ByteString.encodeUtf8("{}");
        KafkaEvent event = mock(KafkaEvent.class);

        given(websocketService.deserialize(bytes)).willReturn(event);

        // when
        sut.onMessage(webSocket, bytes);

        // then
        assertThat(sut.pollTickerQueue()).isNull();
    }

    @Test
    @DisplayName("deserialize 결과가 null이면 큐에 적재하지 않는다")
    void onMessage_deserializeNull_ignore() {
        // given
        ByteString bytes = ByteString.encodeUtf8("{}");

        given(websocketService.deserialize(bytes)).willReturn(null);

        // when
        sut.onMessage(webSocket, bytes);

        // then
        assertThat(sut.pollTickerQueue()).isNull();
    }

    @Test
    @DisplayName("같은 종목의 ticker publish interval이 지나지 않으면 큐에 적재하지 않는다")
    void onMessage_sameCodeIntervalNotElapsed_ignore() {
        // given
        ByteString firstBytes = ByteString.encodeUtf8("first");
        ByteString secondBytes = ByteString.encodeUtf8("second");

        UpbitTickerEvent firstEvent = tickerEvent(CODE, 100.0);
        UpbitTickerEvent secondEvent = tickerEvent(CODE, 101.0);

        given(websocketService.deserialize(firstBytes)).willReturn(firstEvent);
        given(websocketService.deserialize(secondBytes)).willReturn(secondEvent);

        given(clock.nowMs())
                .willReturn(10_000L)
                .willReturn(11_000L);

        // when
        sut.onMessage(webSocket, firstBytes);
        sut.onMessage(webSocket, secondBytes);

        // then
        assertThat(sut.pollTickerQueue()).isSameAs(firstEvent);
        assertThat(sut.pollTickerQueue()).isNull();
    }

    @Test
    @DisplayName("같은 종목의 ticker publish interval이 지나면 다음 ticker도 큐에 적재한다")
    void onMessage_sameCodeIntervalElapsed_offerAgain() {
        // given
        ByteString firstBytes = ByteString.encodeUtf8("first");
        ByteString secondBytes = ByteString.encodeUtf8("second");

        UpbitTickerEvent firstEvent = tickerEvent(CODE, 100.0);
        UpbitTickerEvent secondEvent = tickerEvent(CODE, 101.0);

        given(websocketService.deserialize(firstBytes)).willReturn(firstEvent);
        given(websocketService.deserialize(secondBytes)).willReturn(secondEvent);

        given(clock.nowMs())
                .willReturn(10_000L)
                .willReturn(14_000L);

        // when
        sut.onMessage(webSocket, firstBytes);
        sut.onMessage(webSocket, secondBytes);

        // then
        assertThat(sut.pollTickerQueue()).isSameAs(firstEvent);
        assertThat(sut.pollTickerQueue()).isSameAs(secondEvent);
        assertThat(sut.pollTickerQueue()).isNull();
    }

    @Test
    @DisplayName("다른 종목이면 publish interval이 지나지 않아도 각각 큐에 적재한다")
    void onMessage_differentCodeIntervalNotElapsed_offerEachCode() {
        // given
        ByteString firstBytes = ByteString.encodeUtf8("first");
        ByteString secondBytes = ByteString.encodeUtf8("second");

        UpbitTickerEvent firstEvent = tickerEvent(CODE, 100.0);
        UpbitTickerEvent secondEvent = tickerEvent(OTHER_CODE, 101.0);

        given(websocketService.deserialize(firstBytes)).willReturn(firstEvent);
        given(websocketService.deserialize(secondBytes)).willReturn(secondEvent);

        given(clock.nowMs())
                .willReturn(10_000L)
                .willReturn(11_000L);

        // when
        sut.onMessage(webSocket, firstBytes);
        sut.onMessage(webSocket, secondBytes);

        // then
        assertThat(sut.pollTickerQueue()).isSameAs(firstEvent);
        assertThat(sut.pollTickerQueue()).isSameAs(secondEvent);
        assertThat(sut.pollTickerQueue()).isNull();
    }

    @Test
    @DisplayName("ticker code가 없으면 큐에 적재하지 않는다")
    void onMessage_tickerCodeBlank_ignore() {
        // given
        ByteString bytes = ByteString.encodeUtf8("{}");
        UpbitTickerEvent event = tickerEvent("", 100.0);

        given(websocketService.deserialize(bytes)).willReturn(event);

        // when
        sut.onMessage(webSocket, bytes);

        // then
        assertThat(sut.pollTickerQueue()).isNull();
    }

    @Test
    @DisplayName("큐가 가득 차면 추가 이벤트는 버린다")
    void onMessage_queueFull_dropEvent() {
        // given
        sut = createSut(createProperties(Duration.ZERO, 1));

        ByteString firstBytes = ByteString.encodeUtf8("first");
        ByteString secondBytes = ByteString.encodeUtf8("second");

        UpbitTickerEvent firstEvent = tickerEvent(CODE, 100.0);
        UpbitTickerEvent secondEvent = tickerEvent(CODE, 101.0);

        given(websocketService.deserialize(firstBytes)).willReturn(firstEvent);
        given(websocketService.deserialize(secondBytes)).willReturn(secondEvent);

        given(clock.nowMs())
                .willReturn(10_000L)
                .willReturn(10_001L);

        // when
        sut.onMessage(webSocket, firstBytes);
        sut.onMessage(webSocket, secondBytes);

        // then
        assertThat(sut.pollTickerQueue()).isSameAs(firstEvent);
        assertThat(sut.pollTickerQueue()).isNull();
    }

    private UpbitWebsocketListener createSut(UpbitProperties properties) {
        UpbitWebsocketListener listener = new UpbitWebsocketListener(
                websocketService,
                properties,
                clock,
                marketClient
        );

        listener.init();

        return listener;
    }

    private UpbitProperties createProperties(Duration tickerPublishInterval, int tickerQueueCapacity) {
        return new UpbitProperties(
                new UpbitProperties.Websocket(
                        "wss://api.upbit.com/websocket/v1",
                        "test",
                        tickerPublishInterval,
                        tickerQueueCapacity
                ),
                new UpbitProperties.Ticker(
                        new UpbitProperties.Ticker.Alert(
                                3,
                                Duration.ofSeconds(10)
                        )
                ),
                new UpbitProperties.Store(
                        new UpbitProperties.Store.StoreTicker(
                                "upbit-ticker-store",
                                Duration.ofMinutes(3),
                                Duration.ofMinutes(3),
                                false
                        )
                )
        );
    }

    private MarketResponse marketResponse() {
        return new MarketResponse(
                1L,
                UpbitWebsocketListenerTest.CODE,
                UpbitWebsocketListenerTest.CODE.replace("KRW-", ""),
                "테스트",
                "Test"
        );
    }

    private UpbitTickerEvent tickerEvent(String code, Double tradePrice) {
        return new UpbitTickerEvent(
                "ticker",
                code,
                null,
                null,
                null,
                tradePrice,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
