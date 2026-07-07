package org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload;

import org.example.common.validation.ValidationResult;

public record StompChatMessageAckPayload(
        String id,
        String clientMessageId,
        boolean success,
        long ts,
        ValidationResult errors,
        String errorCode
) {

    public static StompChatMessageAckPayload ofSuccess(String id, String clientMessageId, boolean success, long ts) {
        return new StompChatMessageAckPayload(id, clientMessageId, success, ts, null, null);
    }

    public static StompChatMessageAckPayload ofFailure(String clientMessageId, String errorCode) {
        return new StompChatMessageAckPayload(null, clientMessageId, false, 0L, null, errorCode);
    }
}
