package org.example.chat.chatmessage.application.port.in;

import org.example.chat.chatmessage.application.event.dlq.ChatMessagePersistDlqEvent;

public interface ChatMessageDlqHandler {
    void handle(ChatMessagePersistDlqEvent event);
}
