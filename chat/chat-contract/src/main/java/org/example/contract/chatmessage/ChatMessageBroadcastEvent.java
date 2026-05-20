package org.example.contract.chatmessage;

import java.util.Set;

public record ChatMessageBroadcastEvent(
        ChatMessagePayload payload,
        Set<String> memberIds,
        String clientMessageId
) {
}
