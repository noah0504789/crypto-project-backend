package org.example.websocket.gateway.stomp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotBlank
        String clientMessageId,
        @NotBlank
        String roomId,
        @NotBlank
        String writerId,
        @NotBlank
        @Size(max = 1000)
        String content
) {
}
