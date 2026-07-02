package org.example.chat.chatmessage.domain.event.handler;

import org.example.chat.chatmessage.domain.event.dlq.ChatMessagePersistDlqEvent;

public interface ChatMessageDlqHandler {
    void handle(ChatMessagePersistDlqEvent event);
}
