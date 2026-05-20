package org.example.chatmessage.domain.port;

import org.example.chatmessage.domain.model.event.ChatMessagePersistEvent;

public interface ChatMessageEventHandler {
    void handle(ChatMessagePersistEvent event, String txId);
}
