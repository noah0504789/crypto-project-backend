package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.service.query.ListMyChatRoomsQuery;
import org.example.chat.chatroom.application.service.query.ListPopularChatRoomsQuery;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomQueryRepairService {

    private final ChatRoomCachePort cache;
    private final ChatRoomPersistencePort persistence;
    private final DistributedLockExecutor distributedLockExecutor;

    public ChatRoom repairRoom(String roomId) {
        return distributedLockExecutor.execute(
                "chatroom:findById:" + roomId,
                () -> cache.findById(roomId)
                        .orElseGet(() -> loadRoomAndWarmUp(roomId)),
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairRoomsByIds(List<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }

        return roomIds.stream()
                .map(this::repairRoomSafely)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ChatRoom> repairPopularRooms(ChatRoomCategory category, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listMostPopular:" + category.name() + ":" + limit,
                () -> repairCachedRooms(
                        cache.listMostPopular(category, limit),
                        () -> loadPopularRoomsAndWarmUp(category, limit),
                        limit
                ),
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairPopularRoomsAfter(ListPopularChatRoomsQuery query) {
        return distributedLockExecutor.execute(
                "chatroom:listNextPopular:" + query.category().name() + ":" + query.lastId() + ":" + query.lastPopularity() + ":" + query.limit(),
                () -> repairCachedRooms(
                        cache.listNextPopular(query.category(), query.lastId(), query.lastPopularity(), query.limit()),
                        () -> loadPopularRoomsAfterAndWarmUp(query.category(), query.lastId(), query.lastPopularity(), query.limit()),
                        query.limit()
                ),
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairMyRooms(String memberId, int limit) {
        return distributedLockExecutor.execute(
                "chatroom:listLatestActive:" + memberId + ":" + limit,
                () -> repairCachedRooms(
                        cache.listLatestActive(memberId, limit),
                        () -> loadMyRoomsAndWarmUp(memberId, limit),
                        limit
                ),
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    public List<ChatRoom> repairMyRoomsBefore(ListMyChatRoomsQuery query, Long score) {
        return distributedLockExecutor.execute(
                "chatroom:listActiveBefore:" + query.memberId() + ":" + query.lastId() + ":" + score + ":" + query.limit(),
                () -> repairCachedRooms(
                        cache.listActiveBefore(query.memberId(), query.lastId(), score, query.limit()),
                        () -> loadMyRoomsBeforeAndWarmUp(query.memberId(), query.lastId(), score, query.limit()),
                        query.limit()
                ),
                DistributedLockPolicy.CACHE_WARM_UP
        );
    }

    private List<ChatRoom> repairCachedRooms(
            ChatRoomCacheLookupResult cached,
            Supplier<List<ChatRoom>> noIndexLoader,
            int limit
    ) {
        if (cached.hasNoIndex()) {
            return noIndexLoader.get();
        }

        if (cached.isAllHit()) {
            return cached.hits();
        }

        List<ChatRoom> repaired = repairRoomsByIds(cached.misses());

        return mergeRoomsByOriginalOrder(
                cached.orderedIds(),
                cached.hits(),
                repaired,
                limit
        );
    }

    private ChatRoom loadRoomAndWarmUp(String roomId) {
        ChatRoom stored = persistence.findByIdWithLatest(roomId)
                .orElseThrow(() -> new ChatRoomNotFoundException(roomId));

        warmUpSafely(stored);

        return stored;
    }

    private List<ChatRoom> loadPopularRoomsAndWarmUp(ChatRoomCategory category, int limit) {
        return warmUpAndReturn(persistence.listMostPopular(category, limit));
    }

    private List<ChatRoom> loadPopularRoomsAfterAndWarmUp(
            ChatRoomCategory category,
            String lastId,
            Long lastPopularity,
            int limit
    ) {
        return warmUpAndReturn(
                persistence.listNextPopular(category, lastId, lastPopularity, limit)
        );
    }

    private List<ChatRoom> loadMyRoomsAndWarmUp(String memberId, int limit) {
        return warmUpAndReturn(persistence.listLatestActive(memberId, limit));
    }

    private List<ChatRoom> loadMyRoomsBeforeAndWarmUp(
            String memberId,
            String lastId,
            Long score,
            int limit
    ) {
        return warmUpAndReturn(
                persistence.listActiveBefore(memberId, lastId, score, limit)
        );
    }

    private List<ChatRoom> warmUpAndReturn(List<ChatRoom> rooms) {
        if (rooms.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(rooms);

        return rooms;
    }

    private ChatRoom repairRoomSafely(String roomId) {
        try {
            return repairRoom(roomId);
        } catch (RuntimeException e) {
            log.warn(
                    "[chatroom cache partial repair skipped] roomId={}, reason={}",
                    roomId,
                    e.getMessage()
            );
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
            cache.warmUpList(rooms, toPopularityScores(rooms));
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom warmUpList failed. size={}", rooms.size(), e);
        }
    }

    private Map<String, Double> toPopularityScores(List<ChatRoom> rooms) {
        Map<String, Double> popularities = new HashMap<>();
        rooms.forEach(room -> popularities.put(room.getId(), room.getPopularity()));

        return popularities;
    }

    private List<ChatRoom> mergeRoomsByOriginalOrder(
            List<String> orderedIds,
            List<ChatRoom> hits,
            List<ChatRoom> repaired,
            int limit
    ) {
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