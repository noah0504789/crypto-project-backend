package org.example.chat.chatmessage.domain.port;

import org.example.chat.chatmessage.domain.event.ChatMessagePersistEvent;

public interface ChatMessageEventHandler {
    void handle(ChatMessagePersistEvent event, String txId);
}
