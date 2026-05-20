package org.example.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.List;

/*
[
    { ticket field },
    { type field },
    { type field (optional) },
    { format field (optional) }
]
*/

public record UpbitWebsocketRequest(
        @JsonValue
        List<Object> payload
) {

    public static Builder builder(String ticket) {
        return new Builder(ticket);
    }

    public static class Builder {

        private final String ticket;
        private final List<TypeField> typeFields = new ArrayList<>();

        public Builder(String ticket) {
            this.ticket = ticket;
        }

        public Builder addTicker(List<String> codes, Boolean snapshotOnly, Boolean realtimeOnly) {
            typeFields.add(new TypeField("ticker", codes, snapshotOnly, realtimeOnly));
            return this;
        }

        public UpbitWebsocketRequest build() {
            List<Object> result = new ArrayList<>();
            result.add(new TicketField(ticket));
            result.addAll(typeFields);

            return new UpbitWebsocketRequest(List.copyOf(result));
        }
    }

    private record TicketField(String ticket) { }

    private record TypeField(
            String type,
            List<String> codes,

            @JsonProperty("is_only_snapshot")
            Boolean isOnlySnapshot,

            @JsonProperty("is_only_realtime")
            Boolean isOnlyRealtime
    ) { }
}
