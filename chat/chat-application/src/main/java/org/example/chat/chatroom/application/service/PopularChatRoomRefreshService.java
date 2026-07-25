package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.port.in.PopularChatRoomRefreshUseCase;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.service.ChatRoomPopularityCalculator;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 인기방 인기도를 주기적으로 재계산한다(스케줄러가 호출). 메시지 저장 시 실시간 갱신을 하지 않고,
 * category별로 방 전체를 스캔해 {@link ChatRoomPopularityCalculator} 산식으로 popularity를 계산한 뒤:
 * <ul>
 *   <li>Mongo 각 방의 `popularity` 필드를 bulk 갱신(인기방 정렬/커서 소스)</li>
 *   <li>상위 {@value #POPULAR_INDEX_SIZE}개로 Redis 인기방 zset을 통째 재구축</li>
 * </ul>
 * 실행 간 값은 다소 stale하며, 그 사이 캐시 미스는 on-read 복구(`ChatRoomQueryRepairService`)가 처리한다.
 * 전체 스캔은 산식이 msgCnt 외 항으로 갈라져도 정확한 top-N을 보장하기 위한 선택이다(방 수 유한 전제).
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
                refreshCategory(category);
            } catch (RuntimeException e) {
                log.warn("[popularity] rebuild failed. category={}", category, e);
            }
        }
    }

    private void refreshCategory(ChatRoomCategory category) {
        List<ChatRoom> rooms = persistence.listRoomsForPopularityRecompute(category);

        if (rooms.isEmpty()) {
            cache.rebuildPopularIndex(category, List.of());
            return;
        }

        Map<String, Long> popularities = new LinkedHashMap<>();
        rooms.forEach(room ->
                popularities.put(room.getId(), Math.round(ChatRoomPopularityCalculator.calculate(room)))
        );

        persistence.updatePopularities(popularities);

        List<ChatRoom> topRooms = rooms.stream()
                .sorted(Comparator
                        .comparingLong((ChatRoom room) -> popularities.get(room.getId())).reversed()
                        .thenComparing(Comparator.comparing(ChatRoom::getId).reversed()))
                .limit(POPULAR_INDEX_SIZE)
                .toList();

        cache.rebuildPopularIndex(category, topRooms);
    }
}
