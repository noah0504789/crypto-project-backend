package org.example.chat.chatmessage.application.port.in;

import org.example.chat.chatmessage.application.event.ChatMessagePersistEvent;

import java.util.List;

public interface ChatMessageEventHandler {
    void handle(ChatMessagePersistEvent event, String txId);

    default void handleBatch(List<ChatMessagePersistEvent> events, String txId) {
        events.forEach(event -> handle(event, txId));
    }
}
