package org.example.upbitconnector.adapter.out.upbit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UpbitWebsocketRequestUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("티커 구독 요청을 업비트 WebSocket JSON 배열 형식으로 만든다")
    void serializesTickerSubscription() throws Exception {
        // given
        UpbitWebsocketRequest request =
                UpbitWebsocketRequest.ticker("test-ticket", List.of("KRW-BTC", "KRW-ETH"));

        // when
        String json = objectMapper.writeValueAsString(request);

        // then
        assertThat(json)
                .isEqualTo("[{\"ticket\":\"test-ticket\"},"
                        + "{\"type\":\"ticker\",\"codes\":[\"KRW-BTC\",\"KRW-ETH\"],"
                        + "\"is_only_snapshot\":false,\"is_only_realtime\":true}]");
    }
}
