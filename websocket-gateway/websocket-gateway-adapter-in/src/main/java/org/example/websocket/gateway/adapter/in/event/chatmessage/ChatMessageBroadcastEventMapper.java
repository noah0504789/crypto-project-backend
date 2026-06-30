package org.example.websocket.gateway.adapter.in.event.chatmessage;

import org.example.contract.chatmessage.ChatMessageBroadcastEvent;
import org.example.contract.chatmessage.ChatMessagePayload;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageBroadcastEventMapper {

    public ChatMessageBroadcastCommand toCommand(ChatMessageBroadcastEvent event) {
        ChatMessagePayload payload = event.payload();

        return new ChatMessageBroadcastCommand(
                payload.id(),
                payload.roomId(),
                payload.writerId(),
                payload.content(),
                payload.createdAt() == null ? 0L : payload.createdAt().toEpochMilli(),
                event.memberIds(),
                event.clientMessageId()
        );
    }
}