package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.service.result.ChatRoomCacheLookupResult;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.common.redis.lock.DistributedLockExecutor;
import org.example.common.redis.lock.DistributedLockPolicy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public List<ChatRoom> repairByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .map(this::repairByIdSafely)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ChatRoom> repairMostPopular(ChatRoomCategory category, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listMostPopular:" + category.name() + ":" + limit,
                () -> {
                    ChatRoomCacheLookupResult cached = cache.listMostPopular(category, limit);

                    if (cached.hasNoIndex()) {
                        return loadMostPopularAndWarmUp(category, limit);
                    }

                    if (cached.isAllHit()) {
                        return cached.hits();
                    }

                    List<ChatRoom> repaired = repairByIds(cached.misses());

                    return mergeByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, limit);
                },
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listNextPopular:" + category.name() + ":" + lastId + ":" + lastPopularity + ":" + limit,
                () -> {
                    ChatRoomCacheLookupResult cached = cache.listNextPopular(category, lastId, lastPopularity, limit);

                    if (cached.hasNoIndex()) {
                        return loadNextPopularAndWarmUp(category, lastId, lastPopularity, limit);
                    }

                    if (cached.isAllHit()) {
                        return cached.hits();
                    }

                    List<ChatRoom> repaired = repairByIds(cached.misses());

                    return mergeByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, limit);
                },
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairLatestActive(String memberId, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listLatestActive:" + memberId + ":" + limit,
                () -> {
                    ChatRoomCacheLookupResult cached = cache.listLatestActive(memberId, limit);

                    if (cached.hasNoIndex()) {
                        return loadLatestActiveAndWarmUp(memberId, limit);
                    }

                    if (cached.isAllHit()) {
                        return cached.hits();
                    }

                    List<ChatRoom> repaired = repairByIds(cached.misses());

                    return mergeByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, limit);
                },
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairActiveBefore(String memberId, String lastId, Long score, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listActiveBefore:" + memberId + ":" + lastId + ":" + score + ":" + limit,
                () -> {
                    ChatRoomCacheLookupResult cached = cache.listActiveBefore(memberId, lastId, score, limit);

                    if (cached.hasNoIndex()) {
                        return loadActiveBeforeAndWarmUp(memberId, lastId, score, limit);
                    }

                    if (cached.isAllHit()) {
                        return cached.hits();
                    }

                    List<ChatRoom> repaired = repairByIds(cached.misses());

                    return mergeByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, limit);
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

    private ChatRoom repairByIdSafely(String id) {
        try {
            return repairFindById(id);
        } catch (RuntimeException e) {
            log.warn("[chatroom cache partial repair skipped] roomId={}, reason={}", id, e.getMessage());
            return null;
        }
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

    private List<ChatRoom> mergeByOriginalOrder(List<String> orderedIds, List<ChatRoom> hits, List<ChatRoom> repaired, int limit) {
        Map<String, ChatRoom> chatRoomMap = Stream.concat(hits.stream(), repaired.stream())
                .collect(Collectors.toMap(
                        ChatRoom::getId,
                        Function.identity(),
                        (left, right) -> left
                ));

        return orderedIds.stream()
                .map(chatRoomMap::get)
                .filter(Objects::nonNull)
                .limit(limit)
                .toList();
    }
}