package org.example.marketdetection.upbit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import org.example.common.event.KafkaEvent;
import org.example.common.time.Clock;
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

@ExtendWith(MockitoExtension.class)
class UpbitWebsocketListenerUnitTest {

    private static final String CODE = "KRW-BTC";
    private static final String OTHER_CODE = "KRW-ETH";

    @Mock private UpbitWebsocketService websocketService;

    @Mock private Clock clock;

    @Mock private MarketClient marketClient;

    @Mock private UpbitTickerCoalescingBuffer tickerBuffer;

    @Mock private WebSocket webSocket;

    @Mock private Response response;

    private UpbitWebsocketListener sut;

    @BeforeEach
    void setUp() {
        sut = createSut(createProperties(Duration.ofSeconds(3), 100));
    }

    @Test
    @DisplayName("웹소켓이 열리면 enabled market 코드로 구독을 요청한다")
    void onOpen_subscribeEnabledMarkets() {
        // given
        given(marketClient.getEnabledMarkets()).willReturn(List.of(marketResponse()));

        // when
        sut.onOpen(webSocket, response);

        // then
        verify(websocketService).subscribe(webSocket, List.of(CODE));
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
        given(tickerBuffer.offer(event)).willReturn(true);

        // when
        sut.onMessage(webSocket, bytes);

        // then
        verify(tickerBuffer).offer(event);
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
        verifyNoInteractions(tickerBuffer);
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
        verifyNoInteractions(tickerBuffer);
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

        given(clock.nowMs()).willReturn(10_000L).willReturn(11_000L);
        given(tickerBuffer.offer(firstEvent)).willReturn(true);

        // when
        sut.onMessage(webSocket, firstBytes);
        sut.onMessage(webSocket, secondBytes);

        // then
        verify(tickerBuffer).offer(firstEvent);
        verify(tickerBuffer, never()).offer(secondEvent);
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

        given(clock.nowMs()).willReturn(10_000L).willReturn(14_000L);
        given(tickerBuffer.offer(firstEvent)).willReturn(true);
        given(tickerBuffer.offer(secondEvent)).willReturn(true);

        // when
        sut.onMessage(webSocket, firstBytes);
        sut.onMessage(webSocket, secondBytes);

        // then
        verify(tickerBuffer).offer(firstEvent);
        verify(tickerBuffer).offer(secondEvent);
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

        given(clock.nowMs()).willReturn(10_000L).willReturn(11_000L);
        given(tickerBuffer.offer(firstEvent)).willReturn(true);
        given(tickerBuffer.offer(secondEvent)).willReturn(true);

        // when
        sut.onMessage(webSocket, firstBytes);
        sut.onMessage(webSocket, secondBytes);

        // then
        verify(tickerBuffer).offer(firstEvent);
        verify(tickerBuffer).offer(secondEvent);
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
        verifyNoInteractions(tickerBuffer);
    }

    @Test
    @DisplayName("ready queue 등록 실패 시 publish interval 예약을 되돌려 다음 ticker가 재시도한다")
    void onMessage_readyQueueFull_nextTickerRetriesImmediately() {
        // given
        sut = createSut(createProperties(Duration.ofSeconds(3), 1));

        ByteString firstBytes = ByteString.encodeUtf8("first");
        ByteString secondBytes = ByteString.encodeUtf8("second");

        UpbitTickerEvent firstEvent = tickerEvent(CODE, 100.0);
        UpbitTickerEvent secondEvent = tickerEvent(CODE, 101.0);

        given(websocketService.deserialize(firstBytes)).willReturn(firstEvent);
        given(websocketService.deserialize(secondBytes)).willReturn(secondEvent);

        given(clock.nowMs()).willReturn(10_000L).willReturn(10_001L);
        given(tickerBuffer.offer(firstEvent)).willReturn(false);
        given(tickerBuffer.offer(secondEvent)).willReturn(true);

        // when
        sut.onMessage(webSocket, firstBytes);
        sut.onMessage(webSocket, secondBytes);

        // then
        verify(tickerBuffer).offer(firstEvent);
        verify(tickerBuffer).offer(secondEvent);
    }

    private UpbitWebsocketListener createSut(UpbitProperties properties) {
        UpbitWebsocketListener listener =
                new UpbitWebsocketListener(
                        websocketService, properties, clock, marketClient, tickerBuffer);

        listener.init();

        return listener;
    }

    private UpbitProperties createProperties(
            Duration tickerPublishInterval, int tickerReadyQueueCapacity) {
        return new UpbitProperties(
                new UpbitProperties.Websocket(
                        "wss://api.upbit.com/websocket/v1",
                        "test",
                        tickerPublishInterval,
                        tickerReadyQueueCapacity,
                        3),
                new UpbitProperties.Ticker(
                        new UpbitProperties.Ticker.Alert(3, Duration.ofSeconds(10))),
                new UpbitProperties.Store(
                        new UpbitProperties.Store.StoreTicker(
                                "upbit-ticker-store",
                                Duration.ofMinutes(3),
                                Duration.ofMinutes(3),
                                false)));
    }

    private MarketResponse marketResponse() {
        return new MarketResponse(
                1L,
                UpbitWebsocketListenerUnitTest.CODE,
                UpbitWebsocketListenerUnitTest.CODE.replace("KRW-", ""),
                "테스트",
                "Test");
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
                null);
    }
}
