package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.port.in.ChatMessageQueryUsecase;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageQueryService implements ChatMessageQueryUsecase {

    private final ChatMessageCachePort cache;
    private final ChatMessageQueryRepairService queryRepairService;

    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<ChatMessage> listLatest(String roomId, int limit) {
        List<ChatMessage> cached = cache.listLatest(roomId, limit);

        if (!cached.isEmpty()) {
            return cached;
        }

        return queryRepairService.repairLatest(roomId, limit);
    }

    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<ChatMessage> listPrev(String roomId, String lastId, Long lastCreatedAtMillis, int limit) {
        List<ChatMessage> cached = cache.listPrev(roomId, lastId, lastCreatedAtMillis, limit);

        if (!cached.isEmpty()) {
            return cached;
        }

        return queryRepairService.repairPrev(roomId, lastId, lastCreatedAtMillis, limit);
    }
}