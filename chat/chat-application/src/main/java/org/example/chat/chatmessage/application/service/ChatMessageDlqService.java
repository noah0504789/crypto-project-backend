package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import org.example.chat.chatmessage.application.event.ChatMessagePersistEvent;
import org.example.chat.chatmessage.application.event.dlq.ChatMessagePersistDlqEvent;
import org.example.chat.chatmessage.application.port.in.ChatMessageDlqHandler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageDlqService implements ChatMessageDlqHandler {

    private final ChatMessageEventService eventService;

    public void handle(ChatMessagePersistDlqEvent event) {
        eventService.handle(
                new ChatMessagePersistEvent(event.getPayload(), event.getMemberIds()),
                event.getSourceId()
        );
    }
}
