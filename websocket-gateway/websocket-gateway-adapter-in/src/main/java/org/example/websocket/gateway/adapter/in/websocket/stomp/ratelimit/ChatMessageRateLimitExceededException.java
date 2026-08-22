package org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit;

public final class ChatMessageRateLimitExceededException extends RuntimeException {

    private final String clientMessageId;

    public ChatMessageRateLimitExceededException(String clientMessageId) {
        super("Chat message rate limit exceeded");
        this.clientMessageId = clientMessageId;
    }

    public String clientMessageId() {
        return clientMessageId;
    }
}
