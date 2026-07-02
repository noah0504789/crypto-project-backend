package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.example.chat.chatroom.application.service.query.GetMyChatRoomQuery;
import org.example.chat.chatroom.application.service.query.ListMyChatRoomsQuery;
import org.example.chat.chatroom.application.service.query.ListPopularChatRoomsQuery;
import org.example.chat.chatroom.application.service.result.ChatRoomCacheLookupResult;
import org.example.chat.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.service.result.MyChatRoomSummary;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomQueryService implements ChatRoomQueryUseCase {

    private final ChatRoomCachePort cache;
    private final ChatRoomPersistencePort persistence;
    private final ChatRoomQueryRepairService queryRepairService;

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public ChatRoom getRoom(String roomId) {
        return cache.findById(roomId)
                .orElseGet(() -> queryRepairService.repairRoom(roomId));
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public MyChatRoomSummary getMyRoom(GetMyChatRoomQuery query) {
        return cache.findById(query.roomId())
                .map(room -> toMyChatRoomSummaryWithLastRead(room, query.memberId()))
                .orElseGet(() -> toMyChatRoomSummaryWithPersistedLastRead(queryRepairService.repairRoom(query.roomId()), query.memberId()));
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<ChatRoom> listPopularRooms(ListPopularChatRoomsQuery query) {
        ChatRoomCacheLookupResult cached = query.firstPage()
                ? cache.listMostPopular(query.category(), query.limit())
                : cache.listNextPopular(query.category(), query.lastId(), query.lastPopularity(), query.limit());

        return resolveRooms(
                cached,
                () -> query.firstPage()
                        ? queryRepairService.repairPopularRooms(query.category(), query.limit())
                        : queryRepairService.repairPopularRoomsAfter(query),
                query.limit()
        );
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<MyChatRoomSummary> listMyRooms(ListMyChatRoomsQuery query) {
        return query.firstPage() ? listLatestMyRooms(query) : listMyRoomsBefore(query);
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public boolean existsByTitle(String title) {
        return cache.existsByTitle(title)
                .orElseGet(() -> persistence.existsByTitle(title));
    }

    private List<MyChatRoomSummary> listLatestMyRooms(ListMyChatRoomsQuery query) {
        return resolveMyRooms(
                cache.listLatestActive(query.memberId(), query.limit()),
                () -> queryRepairService.repairMyRooms(query.memberId(), query.limit()),
                query
        );
    }

    private List<MyChatRoomSummary> listMyRoomsBefore(ListMyChatRoomsQuery query) {
        Long score = calculateCursorScore(query);

        return resolveMyRooms(
                cache.listActiveBefore(query.memberId(), query.lastId(), score, query.limit()),
                () -> queryRepairService.repairMyRoomsBefore(query, score),
                query
        );
    }

    private List<ChatRoom> resolveRooms(
            ChatRoomCacheLookupResult cached,
            Supplier<List<ChatRoom>> noIndexRepair,
            int limit
    ) {
        if (cached.hasNoIndex()) {
            return noIndexRepair.get();
        }

        if (cached.isAllHit()) {
            return cached.hits();
        }

        List<ChatRoom> repaired = queryRepairService.repairRoomsByIds(cached.misses());

        return mergeRoomsByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, limit);
    }

    private List<MyChatRoomSummary> resolveMyRooms(
            ChatRoomCacheLookupResult cached,
            Supplier<List<ChatRoom>> noIndexRepair,
            ListMyChatRoomsQuery query
    ) {
        if (cached.hasNoIndex()) {
            return noIndexRepair.get()
                    .stream()
                    .map(room -> toMyChatRoomSummaryWithPersistedLastRead(room, query.memberId()))
                    .toList();
        }

        return resolveMyRoomSummaries(query, cached);
    }

    private List<MyChatRoomSummary> resolveMyRoomSummaries(ListMyChatRoomsQuery query, ChatRoomCacheLookupResult cached) {
        if (cached.isAllHit()) {
            return cached.hits()
                    .stream()
                    .map(room -> toMyChatRoomSummaryWithLastRead(room, query.memberId()))
                    .toList();
        }

        List<ChatRoom> repaired = queryRepairService.repairRoomsByIds(cached.misses());

        return mergeMyRoomSummariesByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, query.memberId(), query.limit());
    }

    private MyChatRoomSummary toMyChatRoomSummaryWithLastRead(ChatRoom room, String memberId) {
        LastReadResult lastRead = getLastReadSeq(room.getId(), memberId);

        if (lastRead.cacheMiss()) {
            refreshActiveCacheSafely(room, memberId, lastRead.seq());
        }

        return MyChatRoomSummary.fromRoom(room, lastRead.seq());
    }

    private MyChatRoomSummary toMyChatRoomSummaryWithPersistedLastRead(ChatRoom room, String memberId) {
        Long lastReadSeq = persistence.getLastReadSeq(room.getId(), memberId);

        refreshActiveCacheSafely(room, memberId, lastReadSeq);

        return MyChatRoomSummary.fromRoom(room, lastReadSeq);
    }

    private LastReadResult getLastReadSeq(String roomId, String memberId) {
        return cache.getLastMsgSeq(roomId, memberId)
                .map(LastReadResult::hit)
                .orElseGet(() -> LastReadResult.miss(persistence.getLastReadSeq(roomId, memberId)));
    }

    private void refreshActiveCacheSafely(ChatRoom room, String memberId, Long lastReadSeq) {
        try {
            cache.updateLastRead(room.getId(), memberId, lastReadSeq);

            boolean unread = room.hasUnread(lastReadSeq);

            long score = unread
                    ? MyChatRoomScoreCalculator.unread(room.getLastMsgCreatedAtMs())
                    : MyChatRoomScoreCalculator.read(room.getLastMsgCreatedAtMs());

            cache.updateRecentScore(room.getId(), memberId, score);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom active cache refresh failed. roomId={}, memberId={}, lastReadSeq={}",
                    room.getId(),
                    memberId,
                    lastReadSeq,
                    e
            );
        }
    }

    private Long calculateCursorScore(ListMyChatRoomsQuery query) {
        return query.cursorUnread()
                ? MyChatRoomScoreCalculator.unread(query.cursorLastMsgCreatedAt())
                : MyChatRoomScoreCalculator.read(query.cursorLastMsgCreatedAt());
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

    private List<MyChatRoomSummary> mergeMyRoomSummariesByOriginalOrder(
            List<String> orderedIds,
            List<ChatRoom> hits,
            List<ChatRoom> repaired,
            String memberId,
            int limit
    ) {
        Map<String, MyChatRoomSummary> summaryMap = new HashMap<>();

        for (ChatRoom room : hits) {
            summaryMap.put(room.getId(), toMyChatRoomSummaryWithLastRead(room, memberId));
        }

        for (ChatRoom room : repaired) {
            summaryMap.putIfAbsent(room.getId(), toMyChatRoomSummaryWithPersistedLastRead(room, memberId));
        }

        return orderedIds.stream()
                .map(summaryMap::get)
                .filter(Objects::nonNull)
                .limit(limit)
                .toList();
    }

    private record LastReadResult(Long seq, boolean cacheMiss) {

        private static LastReadResult hit(Long seq) {
            return new LastReadResult(seq, false);
        }

        private static LastReadResult miss(Long seq) {
            return new LastReadResult(seq, true);
        }
    }
}