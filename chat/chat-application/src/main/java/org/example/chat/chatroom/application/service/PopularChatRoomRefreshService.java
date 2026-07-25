package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.port.in.PopularChatRoomRefreshUseCase;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 인기방 zset을 주기적으로 재계산해 재구축한다. 메시지 저장 시 실시간 증분(ZINCRBY)을 하지 않고,
 * 이 서비스가 category별 상위 후보를 Mongo에서 로드해 {@link ChatRoomPopularityCalculator} 기준으로
 * zset을 통째 다시 그린다(스케줄러가 호출). 실행 간 zset은 다소 stale하며, 그 사이 캐시 미스는
 * on-read 복구(`ChatRoomQueryRepairService`)가 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopularChatRoomRefreshService implements PopularChatRoomRefreshUseCase {

    private static final int POPULAR_INDEX_SIZE = 100;

    private final ChatRoomPersistencePort persistence;
    private final ChatRoomCachePort cache;

    @Override
    public void refresh() {
        for (ChatRoomCategory category : ChatRoomCategory.values()) {
            try {
                List<ChatRoom> rooms = persistence.listPopularRooms(category, POPULAR_INDEX_SIZE);
                cache.rebuildPopularIndex(category, rooms);
            } catch (RuntimeException e) {
                log.warn("[popularity] rebuild failed. category={}", category, e);
            }
        }
    }
}
