package org.example.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.common.exception.ChatRoomNotFoundException;
import org.example.common.redis.DistributedLockExecutor;
import org.example.common.redis.DistributedLockPolicy;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomQueryRepairService {

    private final ChatRoomCachePort cache;
    private final ChatRoomPersistencePort persistence;
    private final DistributedLockExecutor distributedLockExecutor;

    public ChatRoom repairFindById(String id) {
        return distributedLockExecutor.execute(
                "chatroom:findById:" + id,
                () -> cache.findById(id)
                        .orElseGet(() -> loadRoomAndWarmUp(id)),
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairMostPopular(ChatRoomCategory category, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listMostPopular:" + category.name() + ":" + limit,
                () -> {
                    List<ChatRoom> cached = cache.listMostPopular(category, limit);

                    if (!cached.isEmpty()) {
                        return cached;
                    }

                    return loadMostPopularAndWarmUp(category, limit);
                },
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listNextPopular:" + category.name() + ":" + lastId + ":" + lastPopularity + ":" + limit,
                () -> {
                    List<ChatRoom> cached = cache.listNextPopular(category, lastId, lastPopularity, limit);

                    if (!cached.isEmpty()) {
                        return cached;
                    }

                    return loadNextPopularAndWarmUp(category, lastId, lastPopularity, limit);
                },
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairLatestActive(String memberId, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listLatestActive:" + memberId + ":" + limit,
                () -> {
                    List<ChatRoom> cached = cache.listLatestActive(memberId, limit);

                    if (!cached.isEmpty()) {
                        return cached;
                    }

                    return loadLatestActiveAndWarmUp(memberId, limit);
                },
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairActiveBefore(String memberId, String lastId, Long score, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listActiveBefore:" + memberId + ":" + lastId + ":" + score + ":" + limit,
                () -> {
                    List<ChatRoom> cached = cache.listActiveBefore(memberId, lastId, score, limit);

                    if (!cached.isEmpty()) {
                        return cached;
                    }

                    return loadActiveBeforeAndWarmUp(memberId, lastId, score, limit);
                },
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    private ChatRoom loadRoomAndWarmUp(String id) {
        ChatRoom stored = persistence.findByIdWithLatest(id)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));

        warmUpSafely(stored);

        return stored;
    }

    private List<ChatRoom> loadMostPopularAndWarmUp(ChatRoomCategory category, int limit) {
        List<ChatRoom> stored = persistence.listMostPopular(category, limit);

        if (stored.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(stored);

        return stored;
    }

    private List<ChatRoom> loadNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit) {
        return persistence.listNextPopular(category, lastId, lastPopularity, limit);
    }

    private List<ChatRoom> loadNextPopularAndWarmUp(ChatRoomCategory category, String lastId, Long lastPopularity, int limit) {
        List<ChatRoom> stored = loadNextPopular(category, lastId, lastPopularity, limit);

        if (stored.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(stored);

        return stored;
    }

    private List<ChatRoom> loadLatestActiveAndWarmUp(String memberId, int limit) {
        List<ChatRoom> stored = persistence.listLatestActive(memberId, limit);

        if (stored.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(stored);

        return stored;
    }

    private List<ChatRoom> loadActiveBeforeAndWarmUp(String memberId, String lastId, Long score, int limit) {
        List<ChatRoom> stored = persistence.listActiveBefore(memberId, lastId, score, limit);

        if (stored.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(stored);

        return stored;
    }

    private void warmUpSafely(ChatRoom room) {
        try {
            cache.warmUp(room);
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom warmUp failed. roomId={}", room.getId(), e);
        }
    }

    private void warmUpListSafely(List<ChatRoom> rooms) {
        if (rooms.isEmpty()) {
            return;
        }

        try {
            cache.warmUpList(rooms, popularitiesOf(rooms));
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom warmUpList failed. size={}", rooms.size(), e);
        }
    }

    private Map<String, Double> popularitiesOf(List<ChatRoom> rooms) {
        Map<String, Double> popularities = new HashMap<>();
        rooms.forEach(room -> popularities.put(room.getId(), room.getPopularity()));
        return popularities;
    }
}