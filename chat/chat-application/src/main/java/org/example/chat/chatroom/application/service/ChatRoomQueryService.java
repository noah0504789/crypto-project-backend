package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.dto.ChatRoomCacheLookupResult;
import org.example.chat.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.query.MyChatRoomSummary;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
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
    public ChatRoom findById(String id) {
        return cache.findById(id)
                .orElseGet(() -> queryRepairService.repairFindById(id));
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public MyChatRoomSummary findActive(String id, String memberId) {
        return cache.findById(id)
                .map(room -> toMyChatRoomSummaryWithLastRead(room, memberId))
                .orElseGet(() -> toMyChatRoomSummaryWithPersistedLastRead(queryRepairService.repairFindById(id), memberId));
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<ChatRoom> listMostPopular(ChatRoomCategory category, int limit) {
        ChatRoomCacheLookupResult cached = cache.listMostPopular(category, limit);

        if (cached.hasNoIndex()) {
            return queryRepairService.repairMostPopular(category, limit);
        }

        if (cached.isAllHit()) {
            return cached.hits();
        }

        List<ChatRoom> repaired = queryRepairService.repairByIds(cached.misses());

        return mergeByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, limit);
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<ChatRoom> listNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit) {
        ChatRoomCacheLookupResult cached = cache.listNextPopular(category, lastId, lastPopularity, limit);

        if (cached.hasNoIndex()) {
            return queryRepairService.repairNextPopular(category, lastId, lastPopularity, limit);
        }

        if (cached.isAllHit()) {
            return cached.hits();
        }

        List<ChatRoom> repaired = queryRepairService.repairByIds(cached.misses());

        return mergeByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, limit);
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<MyChatRoomSummary> listLatestActive(String memberId, int limit) {
        ChatRoomCacheLookupResult cached = cache.listLatestActive(memberId, limit);

        if (cached.hasNoIndex()) {
            return queryRepairService.repairLatestActive(memberId, limit)
                    .stream()
                    .map(room -> toMyChatRoomSummaryWithPersistedLastRead(room, memberId))
                    .toList();
        }

        if (cached.isAllHit()) {
            return cached.hits()
                    .stream()
                    .map(room -> toMyChatRoomSummaryWithLastRead(room, memberId))
                    .toList();
        }

        List<ChatRoom> repaired = queryRepairService.repairByIds(cached.misses());

        return mergeActiveSummaryByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, memberId, limit);
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<MyChatRoomSummary> listActiveBefore(String memberId, String lastId, Boolean lastUnreadFlag, Long lastMsgCreatedAt, int limit) {
        long cursorLastMsgCreatedAt = lastMsgCreatedAt == null ? 0L : lastMsgCreatedAt;
        boolean cursorUnread = Boolean.TRUE.equals(lastUnreadFlag);

        Long score = cursorUnread ? MyChatRoomScoreCalculator.unread(cursorLastMsgCreatedAt) : MyChatRoomScoreCalculator.read(cursorLastMsgCreatedAt);

        ChatRoomCacheLookupResult cached = cache.listActiveBefore(memberId, lastId, score, limit);

        if (cached.hasNoIndex()) {
            return queryRepairService.repairActiveBefore(memberId, lastId, score, limit)
                    .stream()
                    .map(room -> toMyChatRoomSummaryWithPersistedLastRead(room, memberId))
                    .toList();
        }

        if (cached.isAllHit()) {
            return cached.hits()
                    .stream()
                    .map(room -> toMyChatRoomSummaryWithLastRead(room, memberId))
                    .toList();
        }

        List<ChatRoom> repaired = queryRepairService.repairByIds(cached.misses());

        return mergeActiveSummaryByOriginalOrder(cached.orderedIds(), cached.hits(), repaired, memberId, limit);
    }

    @Override
    public boolean existsByTitle(String title) {
        return cache.existsByTitle(title)
                .orElseGet(() -> persistence.existsByTitle(title));
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
                .map(seq -> new LastReadResult(seq, false))
                .orElseGet(() -> new LastReadResult(persistence.getLastReadSeq(roomId, memberId), true));
    }

    private void refreshActiveCacheSafely(ChatRoom room, String memberId, Long lastReadSeq) {
        try {
            cache.updateLastRead(room.getId(), memberId, lastReadSeq);

            boolean unread = room.hasUnread(lastReadSeq);
            long score = unread ? MyChatRoomScoreCalculator.unread(room.getLastMsgCreatedAtMs()) : MyChatRoomScoreCalculator.read(room.getLastMsgCreatedAtMs());

            cache.updateRecentScore(room.getId(), memberId, score);
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom active cache refresh failed. roomId={}, memberId={}, lastReadSeq={}", room.getId(), memberId, lastReadSeq, e);
        }
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

    private List<MyChatRoomSummary> mergeActiveSummaryByOriginalOrder(List<String> orderedIds, List<ChatRoom> hits, List<ChatRoom> repaired, String memberId, int limit) {
        Map<String, MyChatRoomSummary> summaryMap = new HashMap<>();

        for (ChatRoom room : hits) {
            summaryMap.put(
                    room.getId(),
                    toMyChatRoomSummaryWithLastRead(room, memberId)
            );
        }

        for (ChatRoom room : repaired) {
            summaryMap.putIfAbsent(
                    room.getId(),
                    toMyChatRoomSummaryWithPersistedLastRead(room, memberId)
            );
        }

        return orderedIds.stream()
                .map(summaryMap::get)
                .filter(Objects::nonNull)
                .limit(limit)
                .toList();
    }

    private record LastReadResult(Long seq, boolean cacheMiss) { }
}
