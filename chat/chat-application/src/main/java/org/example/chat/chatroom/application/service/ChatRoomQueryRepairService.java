package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.service.query.ListMyChatRoomsQuery;
import org.example.chat.chatroom.application.service.query.ListPopularChatRoomsQuery;
import org.example.chat.chatroom.application.service.result.ChatRoomCacheLookupResult;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.properties.MyChatRoomProperties;
import org.example.chat.chatroom.application.service.result.MyChatRoomState;
import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.application.exception.ChatRoomNotFoundException;
import org.example.common.redisson.singleflight.SingleFlight;
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
    private final MyChatRoomProperties myChatRoomProperties;
    private final SingleFlight singleFlight;

    public ChatRoom repairRoom(String roomId) {
        return singleFlight.execute(
                "chatroom:findById:" + roomId,
                () -> cache.findById(roomId)
                        .orElseGet(() -> loadRoomAndWarmUp(roomId))
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
        return singleFlight.execute(
                "chatroom:listMostPopular:" + category.name() + ":" + limit,
                () -> repairCachedRooms(
                        cache.listPopularRooms(category, limit),
                        () -> loadPopularRoomsAndWarmUp(category, limit),
                        limit
                )
        );
    }

    public List<ChatRoom> repairPopularRoomsAfter(ListPopularChatRoomsQuery query) {
        return singleFlight.execute(
                "chatroom:listNextPopular:" + query.category().name() + ":" + query.lastRoomId() + ":" + query.lastPopularity() + ":" + query.limit(),
                () -> repairCachedRooms(
                        cache.listPopularRoomsAfter(query.category(), query.lastRoomId(), query.lastPopularity(), query.limit()),
                        () -> loadPopularRoomsAfterAndWarmUp(query.category(), query.lastRoomId(), query.lastPopularity(), query.limit()),
                        query.limit()
                )
        );
    }

    public List<ChatRoom> repairMyRooms(String memberId, int limit) {
        return singleFlight.execute(
                "chatroom:listLatestActive:" + memberId + ":" + limit,
                () -> repairCachedRooms(
                        cache.listLatestActiveRooms(memberId, limit),
                        () -> rebuildMyRoomIndexAndPage(memberId, null, null, limit),
                        limit
                )
        );
    }

    public List<ChatRoom> repairMyRoomsBefore(ListMyChatRoomsQuery query, Long score) {
        return singleFlight.execute(
                "chatroom:listActiveBefore:" + query.memberId() + ":" + query.lastMsgId() + ":" + score + ":" + query.limit(),
                () -> repairCachedRooms(
                        cache.listActiveRoomsBefore(query.memberId(), query.lastMsgId(), score, query.limit()),
                        () -> rebuildMyRoomIndexAndPage(query.memberId(), query.lastMsgId(), score, query.limit()),
                        query.limit()
                )
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
        ChatRoom stored = persistence.findByIdWithLatestMessage(roomId)
                .orElseThrow(() -> new ChatRoomNotFoundException(roomId));

        warmUpSafely(stored);

        return stored;
    }

    private List<ChatRoom> loadPopularRoomsAndWarmUp(ChatRoomCategory category, int limit) {
        return warmUpAndReturn(persistence.listPopularRooms(category, limit));
    }

    private List<ChatRoom> loadPopularRoomsAfterAndWarmUp(
            ChatRoomCategory category,
            String lastId,
            Long lastPopularity,
            int limit
    ) {
        return warmUpAndReturn(
                persistence.listPopularRoomsAfter(category, lastId, lastPopularity, limit)
        );
    }

    /**
     * 내 방 정렬 인덱스가 통째로 비었을 때 Mongo(durable source)로 다시 만든다.
     *
     * <p>정렬 키 {@code (unread, lastMsgCreatedAt, roomId)} 는 방 쪽 사실과 사용자 읽음 위치가
     * 섞여 있어 Mongo 인덱스 하나로는 정렬할 수 없다. 그래서 사용자의 방을
     * {@code chat.my-room.rebuild-limit} 까지 읽어 여기서 계산·정렬하고, 그 결과를 Redis 인덱스에
     * 통째로 심은 뒤 요청한 페이지만 잘라 돌려준다.
     */
    private List<ChatRoom> rebuildMyRoomIndexAndPage(
            String memberId,
            String cursorRoomId,
            Long cursorScore,
            int limit
    ) {
        List<ScoredChatRoom> scoredRooms = scoreMyRooms(memberId);

        if (scoredRooms.isEmpty()) {
            return List.of();
        }

        warmUpListSafely(scoredRooms.stream().map(ScoredChatRoom::room).toList());
        rebuildActiveIndexSafely(memberId, scoredRooms);

        return scoredRooms.stream()
                .filter(scored -> scored.isBefore(cursorScore, cursorRoomId))
                .limit(limit)
                .map(ScoredChatRoom::room)
                .toList();
    }

    private List<ScoredChatRoom> scoreMyRooms(String memberId) {
        return persistence.listMyRoomStates(memberId, myChatRoomProperties.rebuildLimit())
                .stream()
                .map(ScoredChatRoom::from)
                .sorted(
                        Comparator.comparingLong(ScoredChatRoom::score).reversed()
                                .thenComparing(Comparator.comparing((ScoredChatRoom scored) -> scored.room().getId()).reversed())
                )
                .toList();
    }

    private void rebuildActiveIndexSafely(String memberId, List<ScoredChatRoom> scoredRooms) {
        try {
            Map<String, Long> roomIdToScore = new LinkedHashMap<>();
            scoredRooms.forEach(scored -> roomIdToScore.put(scored.room().getId(), scored.score()));

            cache.rebuildActiveIndex(memberId, roomIdToScore);
        } catch (RuntimeException e) {
            log.warn("[projection] my chatroom index rebuild failed. memberId={}, size={}", memberId, scoredRooms.size(), e);
        }
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
            cache.warmUpList(rooms);
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom warmUpList failed. size={}", rooms.size(), e);
        }
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

    /**
     * 방과 그 사용자의 정렬 점수. 점수 규칙은 {@link MyChatRoomScoreCalculator} 한 곳에서만 온다.
     */
    private record ScoredChatRoom(ChatRoom room, long score) {

        private static ScoredChatRoom from(MyChatRoomState state) {
            ChatRoom room = state.room();
            long lastMsgCreatedAtMs = room.lastMsgCreatedAtMs();

            long score = room.hasUnread(state.lastMsgReadSeq())
                    ? MyChatRoomScoreCalculator.unread(lastMsgCreatedAtMs)
                    : MyChatRoomScoreCalculator.read(lastMsgCreatedAtMs);

            return new ScoredChatRoom(room, score);
        }

        private boolean isBefore(Long cursorScore, String cursorRoomId) {
            if (cursorScore == null) {
                return true;
            }

            if (score != cursorScore) {
                return score < cursorScore;
            }

            return cursorRoomId != null && room.getId().compareTo(cursorRoomId) < 0;
        }
    }
}
