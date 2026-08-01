package org.example.marketdetection.upbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.WebSocket;
import okio.ByteString;
import org.example.common.event.KafkaEvent;
import org.example.marketdetection.infra.exception.UpbitWebsocketException;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UpbitWebsocketServiceUnitTest {

    private static final String CODE = "KRW-BTC";
    private static final String TICKET = "test-ticket";

    @Mock
    private WebSocket webSocket;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UpbitProperties properties = createProperties();

    private UpbitWebsocketService sut;

    @BeforeEach
    void setUp() {
        sut = new UpbitWebsocketService(
                objectMapper,
                properties
        );
    }

    @Test
    @DisplayName("subscribe 호출 시 Upbit 구독 요청 JSON을 WebSocket으로 전송한다")
    void subscribe_sendWebsocketRequestJson() throws Exception {
        // when
        sut.subscribe(webSocket, List.of(CODE));

        // then
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        verify(webSocket).send(jsonCaptor.capture());

        String json = jsonCaptor.getValue();

        assertThat(json).contains("\"ticket\":\"" + TICKET + "\"");
        assertThat(json).contains("\"type\":\"ticker\"");
        assertThat(json).contains("\"codes\":[\"" + CODE + "\"]");
        assertThat(json).contains("\"is_only_snapshot\":false");
        assertThat(json).contains("\"is_only_realtime\":true");
    }

    @Test
    @DisplayName("subscribe 호출 시 중복 코드와 blank 코드는 제거하고 구독 요청을 전송한다")
    void subscribe_duplicateAndBlankCodes_sendDistinctValidCodes() {
        // when
        sut.subscribe(webSocket, Arrays.asList(CODE, CODE, "", " ", null));

        // then
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        verify(webSocket).send(jsonCaptor.capture());

        String json = jsonCaptor.getValue();

        assertThat(json).contains("\"codes\":[\"" + CODE + "\"]");
    }

    @Test
    @DisplayName("subscribe 코드가 비어 있으면 예외를 던진다")
    void subscribe_emptyCodes_throwException() {
        // when & then
        assertThatThrownBy(() -> sut.subscribe(webSocket, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Upbit ticker subscribe codes must not be empty.");

        verifyNoInteractions(webSocket);
    }

    @Test
    @DisplayName("subscribe 코드가 null이면 예외를 던진다")
    void subscribe_nullCodes_throwException() {
        // when & then
        assertThatThrownBy(() -> sut.subscribe(webSocket, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Upbit ticker subscribe codes must not be empty.");

        verifyNoInteractions(webSocket);
    }

    @Test
    @DisplayName("ticker 타입 메시지를 UpbitTickerEvent로 역직렬화한다")
    void deserialize_tickerMessage_returnUpbitTickerEvent() {
        // given
        String json = """
                {
                  "type": "ticker",
                  "code": "KRW-BTC",
                  "trade_price": 100.5,
                  "timestamp": 1710000000000,
                  "stream_type": "REALTIME"
                }
                """;

        ByteString bytes = ByteString.encodeUtf8(json);

        // when
        KafkaEvent result = sut.deserialize(bytes);

        // then
        assertThat(result).isInstanceOf(UpbitTickerEvent.class);

        UpbitTickerEvent event = (UpbitTickerEvent) result;

        assertThat(event.type()).isEqualTo("ticker");
        assertThat(event.code()).isEqualTo(CODE);
        assertThat(event.tradePrice()).isEqualTo(100.5);
        assertThat(event.timestamp()).isEqualTo(1710000000000L);
        assertThat(event.streamType()).isEqualTo("REALTIME");
        assertThat(event.getPartitionKey()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("지원하지 않는 type이면 null을 반환한다")
    void deserialize_unsupportedType_returnNull() {
        // given
        String json = """
                {
                  "type": "trade",
                  "code": "KRW-BTC",
                  "trade_price": 100.5
                }
                """;

        ByteString bytes = ByteString.encodeUtf8(json);

        // when
        KafkaEvent result = sut.deserialize(bytes);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("type 필드가 없으면 null을 반환한다")
    void deserialize_missingType_returnNull() {
        // given
        String json = """
                {
                  "code": "KRW-BTC",
                  "trade_price": 100.5
                }
                """;

        ByteString bytes = ByteString.encodeUtf8(json);

        // when
        KafkaEvent result = sut.deserialize(bytes);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("type 필드가 문자열이 아니면 null을 반환한다")
    void deserialize_invalidType_returnNull() {
        // given
        String json = """
                {
                  "type": 123,
                  "code": "KRW-BTC",
                  "trade_price": 100.5
                }
                """;

        ByteString bytes = ByteString.encodeUtf8(json);

        // when
        KafkaEvent result = sut.deserialize(bytes);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("잘못된 JSON이면 UpbitWebsocketException을 던진다")
    void deserialize_invalidJson_throwException() {
        // given
        ByteString bytes = ByteString.encodeUtf8("{ invalid-json ");

        // when & then
        assertThatThrownBy(() -> sut.deserialize(bytes))
                .isInstanceOf(UpbitWebsocketException.class)
                .hasMessage("Failed to deserialize upbit websocket message");
    }

    private UpbitProperties createProperties() {
        return new UpbitProperties(
                new UpbitProperties.Websocket(
                        "wss://api.upbit.com/websocket/v1",
                        TICKET,
                        Duration.ofSeconds(3),
                        100
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
}
