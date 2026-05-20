package org.example.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import org.example.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatmessage.domain.model.event.dlq.ChatMessagePersistDlqEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageDlqService implements org.example.chatmessage.domain.port.ChatMessageDlqHandler {

    private final ChatMessagePersistencePort persistence;

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatMessagePersistDlqEvent event) {
        ChatMessage domain = ChatMessage.fromPayload(event.getPayload());

        persistence.save(domain);
    }
}
