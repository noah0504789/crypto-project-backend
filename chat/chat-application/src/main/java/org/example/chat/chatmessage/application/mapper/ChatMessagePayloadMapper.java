package org.example.chat.chatmessage.application.mapper;

import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.contract.chatmessage.ChatMessagePayload;

public final class ChatMessagePayloadMapper {

    private ChatMessagePayloadMapper() {
    }

    public static ChatMessage toDomain(ChatMessagePayload payload) {
        return ChatMessage.rehydrate(
                payload.id(),
                payload.roomId(),
                payload.writerId(),
                payload.content(),
                payload.createdAt()
        );
    }

    public static ChatMessagePayload fromDomain(ChatMessage message) {
        return new ChatMessagePayload(
                message.getId(),
                message.getRoomId(),
                message.getWriterId(),
                message.getContent(),
                message.getCreatedAtInstant()
        );
    }
}