package org.example.chat.chatmessage.domain.event.handler;

import org.example.chat.chatmessage.domain.event.ChatMessagePersistEvent;

public interface ChatMessageEventHandler {
    void handle(ChatMessagePersistEvent event, String txId);
}
