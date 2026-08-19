package org.example.upbitconnector.adapter.out.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/** Upbit WebSocket 구독 요청은 객체가 아니라 JSON 배열이다. */
public record UpbitWebsocketRequest(@JsonValue List<Object> payload) {

    public UpbitWebsocketRequest {
        payload = List.copyOf(payload);
    }

    public static UpbitWebsocketRequest ticker(String ticket, List<String> codes) {
        return new UpbitWebsocketRequest(List.of(
                new TicketField(ticket),
                new TypeField("ticker", List.copyOf(codes), false, true)
        ));
    }

    private record TicketField(String ticket) {}

    private record TypeField(
            String type,
            List<String> codes,
            @JsonProperty("is_only_snapshot") boolean isOnlySnapshot,
            @JsonProperty("is_only_realtime") boolean isOnlyRealtime) {}
}
