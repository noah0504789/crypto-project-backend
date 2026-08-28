package org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload;

import java.util.List;

/** 프론트와의 wire 계약. {@code messages} 순서가 서버가 받은 순서다. */
public record StompChatMessageBatchPayload(
        String roomId,
        List<StompChatMessagePayload> messages
) {
}
