package org.example.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.example.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chatroom.application.query.MyChatRoomSummary;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
        List<ChatRoom> cached = cache.listMostPopular(category, limit);

        if (!cached.isEmpty()) {
            return cached;
        }

        return queryRepairService.repairMostPopular(category, limit);
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<ChatRoom> listNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit) {
        List<ChatRoom> cached = cache.listNextPopular(category, lastId, lastPopularity, limit);

        if (!cached.isEmpty()) {
            return cached;
        }

        return queryRepairService.repairNextPopular(category, lastId, lastPopularity, limit);
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<MyChatRoomSummary> listLatestActive(String memberId, int limit) {
        List<ChatRoom> cached = cache.listLatestActive(memberId, limit);

        if (!cached.isEmpty()) {
            return cached.stream()
                    .map(room -> toMyChatRoomSummaryWithLastRead(room, memberId))
                    .toList();
        }

        return queryRepairService.repairLatestActive(memberId, limit).stream()
                .map(room -> toMyChatRoomSummaryWithPersistedLastRead(room, memberId))
                .toList();
    }

    @Override
    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public List<MyChatRoomSummary> listActiveBefore(String memberId, String lastId, Boolean lastUnreadFlag, Long lastMsgCreatedAt, int limit) {
        Long score = ChatRoomActivityScore.calculate(lastMsgCreatedAt == null ? 0L : lastMsgCreatedAt, Boolean.TRUE.equals(lastUnreadFlag));

        List<ChatRoom> cached = cache.listActiveBefore(memberId, lastId, score, limit);

        if (!cached.isEmpty()) {
            return cached.stream()
                    .map(room -> toMyChatRoomSummaryWithLastRead(room, memberId))
                    .toList();
        }

        return queryRepairService.repairActiveBefore(memberId, lastId, score, limit).stream()
                .map(room -> toMyChatRoomSummaryWithPersistedLastRead(room, memberId))
                .toList();
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
            cache.updateRecentScore(room.getId(), memberId, ChatRoomActivityScore.calculate(room.getLastMsgCreatedAtMs(), room.hasUnread(lastReadSeq)));
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom active cache refresh failed. roomId={}, memberId={}, lastReadSeq={}", room.getId(), memberId, lastReadSeq, e);
        }
    }

    private record LastReadResult(Long seq, boolean cacheMiss) { }
}
