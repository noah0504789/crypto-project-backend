package org.example.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chatmessage.application.port.in.ChatMessageQueryUsecase;
import org.example.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.common.redis.DistributedLockExecutor;
import org.example.common.redis.DistributedLockPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                () -> {
                    List<ChatMessage> cached = cache.listLatest(roomId, limit);

                    if (!cached.isEmpty()) {
                        return cached;
                    }

                    return loadLatestAndWarmUp(roomId, limit);
                },
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatMessage> repairPrev(String roomId, String lastId, Long lastCreatedAtMillis, int limit) {
        return distributedLockExecutor.execute(
                "chatmessage:listPrev:" + roomId + ":" + lastId + ":" + lastCreatedAtMillis + ":" + limit,
                () -> {
                    List<ChatMessage> cached = cache.listPrev(roomId, lastId, lastCreatedAtMillis, limit);

                    if (!cached.isEmpty()) {
                        return cached;
                    }

                    return loadPrevAndWarmUp(roomId, lastId, lastCreatedAtMillis, limit);
                },
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    private List<ChatMessage> loadLatestAndWarmUp(String roomId, int limit) {
        List<ChatMessage> stored = persistence.listLatest(roomId, limit);

        if (stored.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(stored, roomId);

        return stored;
    }

    private List<ChatMessage> loadPrevAndWarmUp(String roomId, String lastId, Long lastCreatedAtMillis, int limit) {
        List<ChatMessage> stored = persistence.listPrev(roomId, lastId, lastCreatedAtMillis, limit);

        if (stored.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(stored, roomId);

        return stored;
    }

    private void warmUpListSafely(List<ChatMessage> messages, String roomId) {
        try {
            cache.warmUpList(messages, roomId);
        } catch (RuntimeException e) {
            log.warn("[cache] chatmessage warmUpList failed. roomId={}, size={}", roomId, messages == null ? 0 : messages.size(), e);
        }
    }
}