package org.example.chatmessage.domain.port;

import org.example.chatmessage.domain.model.event.dlq.ChatMessagePersistDlqEvent;

public interface ChatMessageDlqHandler {
    void handle(ChatMessagePersistDlqEvent event);
}
