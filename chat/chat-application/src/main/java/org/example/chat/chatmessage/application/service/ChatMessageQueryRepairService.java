package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.application.service.query.ListChatMessagesQuery;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.common.redis.lock.DistributedLockExecutor;
import org.example.common.redis.lock.DistributedLockPolicy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageQueryRepairService {

    private final ChatMessageCachePort cache;
    private final ChatMessagePersistencePort persistence;
    private final DistributedLockExecutor distributedLockExecutor;

    public List<ChatMessage> repairLatest(String roomId, int limit) {
        return distributedLockExecutor.execute(
                "chatmessage:listLatest:" + roomId + ":" + limit,
                () -> repairCachedMessages(
                        cache.listLatest(roomId, limit),
                        () -> loadLatestAndWarmUp(roomId, limit)
                ),
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatMessage> repairPrev(ListChatMessagesQuery query) {
        return distributedLockExecutor.execute(
                "chatmessage:listPrev:" + query.roomId() + ":" + query.lastId() + ":" + query.cursorCreatedAtMillis() + ":" + query.limit(),
                () -> repairCachedMessages(
                        cache.listPrev(query.roomId(), query.lastId(), query.cursorCreatedAtMillis(), query.limit()),
                        () -> loadPrevAndWarmUp(query)
                ),
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    private List<ChatMessage> repairCachedMessages(List<ChatMessage> cached, Supplier<List<ChatMessage>> noCacheLoader) {
        if (!cached.isEmpty()) {
            return cached;
        }

        return noCacheLoader.get();
    }

    private List<ChatMessage> loadLatestAndWarmUp(String roomId, int limit) {
        List<ChatMessage> stored = persistence.listLatest(roomId, limit);

        if (stored.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(stored, roomId);

        return stored;
    }

    private List<ChatMessage> loadPrevAndWarmUp(ListChatMessagesQuery query) {
        List<ChatMessage> stored = persistence.listPrev(query.roomId(), query.lastId(), query.cursorCreatedAtMillis(), query.limit());

        if (stored.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(stored, query.roomId());

        return stored;
    }

    private void warmUpListSafely(List<ChatMessage> messages, String roomId) {
        try {
            cache.warmUpList(messages, roomId);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatmessage warmUpList failed. roomId={}, size={}",
                    roomId,
                    messages == null ? 0 : messages.size(),
                    e
            );
        }
    }
}