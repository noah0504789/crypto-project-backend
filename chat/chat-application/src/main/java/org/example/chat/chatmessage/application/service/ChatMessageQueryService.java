package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.port.in.ChatMessageQueryUseCase;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.application.service.query.ListChatMessagesQuery;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageQueryService implements ChatMessageQueryUseCase {

    private final ChatMessageCachePort cache;
    private final ChatMessageQueryRepairService queryRepairService;

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<ChatMessage> listMessages(ListChatMessagesQuery query) {
        List<ChatMessage> cached = query.firstPage()
                ? cache.listLatest(query.roomId(), query.limit())
                : cache.listPrev(query.roomId(), query.lastId(), query.cursorCreatedAtMillis(), query.limit());

        if (!cached.isEmpty()) {
            return cached;
        }

        return query.firstPage()
                ? queryRepairService.repairLatest(query.roomId(), query.limit())
                : queryRepairService.repairPrev(query);
    }
}