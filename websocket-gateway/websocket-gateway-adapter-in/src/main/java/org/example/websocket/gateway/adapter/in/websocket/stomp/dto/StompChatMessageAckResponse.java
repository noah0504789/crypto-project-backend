package org.example.websocket.gateway.adapter.in.websocket.stomp.dto;

import org.example.common.validation.ValidationResult;

public record StompChatMessageAckResponse(
        String id,
        String clientMessageId,
        boolean success,
        long ts,
        ValidationResult errors,
        String errorCode
) {

    public static StompChatMessageAckResponse ofFailure(String clientMessageId, String errorCode) {
        return new StompChatMessageAckResponse(null, clientMessageId, false, 0L, null, errorCode);
    }
}
