package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import org.example.chat.chatmessage.application.mapper.ChatMessagePayloadMapper;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatmessage.application.event.dlq.ChatMessagePersistDlqEvent;
import org.example.chat.chatmessage.application.port.in.ChatMessageDlqHandler;
import org.example.contract.chatmessage.ChatMessagePayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageDlqService implements ChatMessageDlqHandler {

    private final ChatMessagePersistencePort persistence;

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatMessagePersistDlqEvent event) {
        ChatMessagePayload payload = event.getPayload();
        ChatMessage domain = ChatMessagePayloadMapper.toDomain(payload);

        persistence.save(domain);
    }
}
