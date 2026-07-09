package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.port.in.ChatMessageQueryUseCase;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.application.service.query.ListChatMessagesQuery;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageQueryService implements ChatMessageQueryUseCase {

    private final ChatMessageCachePort cache;
    private final ChatMessageQueryRepairService queryRepairService;

    @Override
    public List<ChatMessage> listMessages(ListChatMessagesQuery query) {
        List<ChatMessage> cached = query.hasNoCursor()
                ? cache.listLatestMessages(query.roomId(), query.limit())
                : cache.listMessagesBefore(query.roomId(), query.lastMsgId(), query.cursorCreatedAtMs(), query.limit());

        if (!cached.isEmpty()) {
            return cached;
        }

        return query.hasNoCursor()
                ? queryRepairService.repairLatest(query.roomId(), query.limit())
                : queryRepairService.repairPrev(query);
    }
}