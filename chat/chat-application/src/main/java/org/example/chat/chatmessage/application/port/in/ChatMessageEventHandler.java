package org.example.chat.chatmessage.application.port.in;

import org.example.chat.chatmessage.application.event.ChatMessagePersistEvent;

public interface ChatMessageEventHandler {
    void handle(ChatMessagePersistEvent event, String txId);
}
