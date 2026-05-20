package org.example.contract.chatmessage;

import java.time.Instant;

public record ChatMessagePayload(
        String id,
        String roomId,
        String writerId,
        String content,
        Instant createdAt
) {
}
