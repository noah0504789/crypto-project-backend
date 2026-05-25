package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatmessage.domain.event.dlq.ChatMessagePersistDlqEvent;
import org.example.chat.chatmessage.domain.port.ChatMessageDlqHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageDlqService implements ChatMessageDlqHandler {

    private final ChatMessagePersistencePort persistence;

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatMessagePersistDlqEvent event) {
        ChatMessage domain = ChatMessage.fromPayload(event.getPayload());

        persistence.save(domain);
    }
}
