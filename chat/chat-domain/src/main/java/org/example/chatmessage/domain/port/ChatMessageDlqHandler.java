package org.example.chatmessage.domain.port;

import org.example.chatmessage.domain.event.dlq.ChatMessagePersistDlqEvent;

public interface ChatMessageDlqHandler {
    void handle(ChatMessagePersistDlqEvent event);
}
