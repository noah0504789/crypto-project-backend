package org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit.chat-message")
public record ChatMessageRateLimitProperties(
        boolean enabled,
        @Valid Bucket user,
        @Valid Bucket room
) {

    private static final Bucket DEFAULT_USER_BUCKET = new Bucket(3, 5);
    private static final Bucket DEFAULT_ROOM_BUCKET = new Bucket(30, 10);

    public ChatMessageRateLimitProperties {
        user = user == null ? DEFAULT_USER_BUCKET : user;
        room = room == null ? DEFAULT_ROOM_BUCKET : room;
    }

    public record Bucket(
            @Positive long replenishRate,
            @Positive long burstCapacity
    ) {
    }
}
