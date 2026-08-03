package org.example.marketdetection.upbit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.WebSocket;
import okio.ByteString;
import org.example.common.event.KafkaEvent;
import org.example.marketdetection.infra.exception.UpbitWebsocketException;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.dto.UpbitWebsocketRequest;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpbitWebsocketService {

    private static final String TYPE_FIELD = "type";

    private final ObjectMapper objectMapper;
    private final UpbitProperties properties;

    public void subscribe(WebSocket session, List<String> tickerCodes) {
        List<String> codes = tickerCodes == null ?
                List.of() :
                tickerCodes.stream().filter(code -> code != null && !code.isBlank()).distinct().toList();

        if (codes.isEmpty()) {
            throw new IllegalArgumentException("Upbit ticker subscribe codes must not be empty.");
        }

        String ticket = properties.websocket().ticket();

        UpbitWebsocketRequest request = UpbitWebsocketRequest.builder(ticket)
                .addTicker(codes, false, true)
                .build();

        session.send(serializeRequest(request));

        log.info("[upbit] subscribed upbit ticker websocket. codes={}", codes);
    }

    public KafkaEvent deserialize(ByteString bytes) {
        Map<String, Object> payload = deserializePayload(bytes);
        String type = extractType(payload);

        if (UpbitWebsocketType.TICKER.getValue().equals(type)) {
            return objectMapper.convertValue(payload, UpbitTickerEvent.class);
        }

        log.debug("[upbit] unsupported upbit websocket message type. type={}", type);
        return null;
    }

    private String serializeRequest(UpbitWebsocketRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new UpbitWebsocketException("Failed to serialize upbit websocket request", e);
        }
    }

    private Map<String, Object> deserializePayload(ByteString bytes) {
        try {
            return objectMapper.readValue(
                    bytes.toByteArray(),
                    new TypeReference<Map<String, Object>>() { }
            );
        } catch (IOException e) {
            throw new UpbitWebsocketException("Failed to deserialize upbit websocket message", e);
        }
    }

    private String extractType(Map<String, Object> payload) {
        Object type = payload.get(TYPE_FIELD);

        if (!(type instanceof String)) {
            log.warn("[upbit] upbit websocket message type is missing or invalid. payload={}", payload);
            return null;
        }

        return (String) type;
    }
}