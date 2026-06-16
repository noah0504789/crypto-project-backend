package chatroom.service;

import org.example.chat.chatroom.application.dto.ChatRoomCacheLookupResult;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.query.MyChatRoomSummary;
import org.example.chat.chatroom.application.service.ChatRoomActivityScore;
import org.example.chat.chatroom.application.service.ChatRoomQueryRepairService;
import org.example.chat.chatroom.application.service.ChatRoomQueryService;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomQueryServiceTest {

    @Mock
    private ChatRoomCachePort cache;

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomQueryRepairService queryRepairService;

    @InjectMocks
    private ChatRoomQueryService sut;

    @Mock
    private ChatRoom room, room2;

    private final String ROOM_ID = "room-1";
    private final String ROOM_ID_2 = "room-2";
    private final String MEMBER_ID = "member-1";

    @Test
    @DisplayName("findById: 캐시 히트 시 캐시된 채팅방을 반환한다")
    void should_return_cached_room_when_find_by_id_cache_hits() {
        // given
        when(cache.findById(ROOM_ID))
                .thenReturn(Optional.of(room));

        // when
        ChatRoom result = sut.findById(ROOM_ID);

        // then
        assertThat(result).isSameAs(room);

        verify(queryRepairService, never()).repairFindById(anyString());
    }

    @Test
    @DisplayName("findById: 캐시 미스 시 repairFindById로 복구 조회한다")
    void should_repair_room_when_find_by_id_cache_misses() {
        // given
        when(cache.findById(ROOM_ID))
                .thenReturn(Optional.empty());
        when(queryRepairService.repairFindById(ROOM_ID))
                .thenReturn(room);

        // when
        ChatRoom result = sut.findById(ROOM_ID);

        // then
        assertThat(result).isSameAs(room);

        verify(queryRepairService, times(1)).repairFindById(ROOM_ID);
    }

    @Test
    @DisplayName("findActive: 채팅방 캐시와 lastRead 캐시가 모두 히트하면 복구 조회 없이 Summary를 반환한다")
    void should_return_active_summary_without_repair_when_room_and_last_read_cache_hit() {
        // given
        when(room.getId()).thenReturn(ROOM_ID);

        when(cache.findById(ROOM_ID))
                .thenReturn(Optional.of(room));
        when(cache.getLastMsgSeq(ROOM_ID, MEMBER_ID))
                .thenReturn(Optional.of(10L));

        // when
        MyChatRoomSummary result = sut.findActive(ROOM_ID, MEMBER_ID);

        // then
        assertThat(result).isNotNull();

        verify(queryRepairService, never()).repairFindById(anyString());
        verify(persistence, never()).getLastReadSeq(anyString(), anyString());
        verify(cache, never()).updateLastRead(anyString(), anyString(), anyLong());
        verify(cache, never()).updateRecentScore(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("findActive: 채팅방 캐시는 히트하고 lastRead 캐시가 미스하면 영속 저장소 조회 후 active 캐시를 갱신한다")
    void should_refresh_active_cache_when_room_cache_hits_but_last_read_cache_misses() {
        // given
        when(room.getId()).thenReturn(ROOM_ID);
        when(room.getLastMsgCreatedAtMs()).thenReturn(1_000L);
        when(room.hasUnread(10L)).thenReturn(true);

        when(cache.findById(ROOM_ID))
                .thenReturn(Optional.of(room));
        when(cache.getLastMsgSeq(ROOM_ID, MEMBER_ID))
                .thenReturn(Optional.empty());
        when(persistence.getLastReadSeq(ROOM_ID, MEMBER_ID))
                .thenReturn(10L);

        // when
        MyChatRoomSummary result = sut.findActive(ROOM_ID, MEMBER_ID);

        // then
        assertThat(result).isNotNull();

        verify(persistence, times(1)).getLastReadSeq(ROOM_ID, MEMBER_ID);
        verify(cache, times(1)).updateLastRead(ROOM_ID, MEMBER_ID, 10L);
        verify(cache, times(1)).updateRecentScore(
                eq(ROOM_ID),
                eq(MEMBER_ID),
                eq(ChatRoomActivityScore.calculate(1_000L, true))
        );
    }

    @Test
    @DisplayName("findActive: 채팅방 캐시 미스 시 repairFindById로 복구하고 영속 저장소의 lastRead 기준으로 active 캐시를 갱신한다")
    void should_repair_room_and_use_persisted_last_read_when_find_active_room_cache_misses() {
        // given
        when(room.getId()).thenReturn(ROOM_ID);
        when(room.getLastMsgCreatedAtMs()).thenReturn(1_000L);
        when(room.hasUnread(20L)).thenReturn(false);

        when(cache.findById(ROOM_ID))
                .thenReturn(Optional.empty());
        when(queryRepairService.repairFindById(ROOM_ID))
                .thenReturn(room);
        when(persistence.getLastReadSeq(ROOM_ID, MEMBER_ID))
                .thenReturn(20L);

        // when
        MyChatRoomSummary result = sut.findActive(ROOM_ID, MEMBER_ID);

        // then
        assertThat(result).isNotNull();

        verify(queryRepairService, times(1)).repairFindById(ROOM_ID);
        verify(persistence, times(1)).getLastReadSeq(ROOM_ID, MEMBER_ID);
        verify(cache, times(1)).updateLastRead(ROOM_ID, MEMBER_ID, 20L);
        verify(cache, times(1)).updateRecentScore(
                eq(ROOM_ID),
                eq(MEMBER_ID),
                eq(ChatRoomActivityScore.calculate(1_000L, false))
        );
    }

    @Test
    @DisplayName("listMostPopular: 전체 히트 시 인기 채팅방 목록을 캐시에서 반환한다")
    void should_return_cached_most_popular_when_all_hit() {
        // given
        ChatRoomCacheLookupResult cached = allHit(List.of(ROOM_ID), List.of(room));

        when(cache.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(cached);

        // when
        List<ChatRoom> result = sut.listMostPopular(ChatRoomCategory.FREE, 10);

        // then
        assertThat(result).containsExactly(room);

        verify(queryRepairService, never()).repairMostPopular(any(), anyInt());
        verify(queryRepairService, never()).repairByIds(anyList());
    }

    @Test
    @DisplayName("listMostPopular: 인덱스가 없으면 repairMostPopular로 전체 복구 조회한다")
    void should_repair_most_popular_when_index_missing() {
        // given
        List<ChatRoom> repaired = List.of(room, room2);

        when(cache.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(noIndex());
        when(queryRepairService.repairMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(repaired);

        // when
        List<ChatRoom> result = sut.listMostPopular(ChatRoomCategory.FREE, 10);

        // then
        assertThat(result).containsExactly(room, room2);

        verify(queryRepairService, times(1)).repairMostPopular(ChatRoomCategory.FREE, 10);
        verify(queryRepairService, never()).repairByIds(anyList());
    }

    @Test
    @DisplayName("listMostPopular: 일부 미스 시 미스난 roomId만 repairByIds로 복구하고 원래 순서대로 병합한다")
    void should_repair_only_missed_rooms_when_most_popular_partially_misses() {
        // given
        when(room.getId()).thenReturn(ROOM_ID);
        when(room2.getId()).thenReturn(ROOM_ID_2);

        ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                List.of(ROOM_ID, ROOM_ID_2),
                List.of(room),
                List.of(ROOM_ID_2)
        );

        when(cache.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(cached);
        when(queryRepairService.repairByIds(List.of(ROOM_ID_2)))
                .thenReturn(List.of(room2));

        // when
        List<ChatRoom> result = sut.listMostPopular(ChatRoomCategory.FREE, 10);

        // then
        assertThat(result).containsExactly(room, room2);

        verify(queryRepairService, never()).repairMostPopular(any(), anyInt());
        verify(queryRepairService, times(1)).repairByIds(List.of(ROOM_ID_2));
    }

    @Test
    @DisplayName("listNextPopular: 전체 히트 시 다음 인기 채팅방 목록을 캐시에서 반환한다")
    void should_return_cached_next_popular_when_all_hit() {
        // given
        ChatRoomCacheLookupResult cached = allHit(List.of(ROOM_ID), List.of(room));

        when(cache.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10))
                .thenReturn(cached);

        // when
        List<ChatRoom> result = sut.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10);

        // then
        assertThat(result).containsExactly(room);

        verify(queryRepairService, never()).repairNextPopular(any(), anyString(), anyLong(), anyInt());
        verify(queryRepairService, never()).repairByIds(anyList());
    }

    @Test
    @DisplayName("listNextPopular: 인덱스가 없으면 repairNextPopular로 다음 인기 채팅방 목록을 복구 조회한다")
    void should_repair_next_popular_when_index_missing() {
        // given
        List<ChatRoom> repaired = List.of(room, room2);

        when(cache.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10))
                .thenReturn(noIndex());
        when(queryRepairService.repairNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10))
                .thenReturn(repaired);

        // when
        List<ChatRoom> result = sut.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10);

        // then
        assertThat(result).containsExactly(room, room2);

        verify(queryRepairService, times(1))
                .repairNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10);
        verify(queryRepairService, never()).repairByIds(anyList());
    }

    @Test
    @DisplayName("listNextPopular: 일부 미스 시 미스난 roomId만 repairByIds로 복구하고 원래 순서대로 병합한다")
    void should_repair_only_missed_rooms_when_next_popular_partially_misses() {
        // given
        when(room.getId()).thenReturn(ROOM_ID);
        when(room2.getId()).thenReturn(ROOM_ID_2);

        ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                List.of(ROOM_ID, ROOM_ID_2),
                List.of(room),
                List.of(ROOM_ID_2)
        );

        when(cache.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10))
                .thenReturn(cached);
        when(queryRepairService.repairByIds(List.of(ROOM_ID_2)))
                .thenReturn(List.of(room2));

        // when
        List<ChatRoom> result = sut.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10);

        // then
        assertThat(result).containsExactly(room, room2);

        verify(queryRepairService, never()).repairNextPopular(any(), anyString(), anyLong(), anyInt());
        verify(queryRepairService, times(1)).repairByIds(List.of(ROOM_ID_2));
    }

    @Test
    @DisplayName("listLatestActive: 전체 히트 시 lastRead 캐시를 사용해 내 채팅방 Summary 목록을 반환한다")
    void should_return_latest_active_from_cache_and_use_last_read_cache_when_all_hit() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room2.getId()).thenReturn("room-2");

        ChatRoomCacheLookupResult cached = allHit(
                List.of("room-1", "room-2"),
                List.of(room, room2)
        );

        when(cache.listLatestActive(MEMBER_ID, 10))
                .thenReturn(cached);
        when(cache.getLastMsgSeq("room-1", MEMBER_ID))
                .thenReturn(Optional.of(1L));
        when(cache.getLastMsgSeq("room-2", MEMBER_ID))
                .thenReturn(Optional.of(2L));

        // when
        List<MyChatRoomSummary> result = sut.listLatestActive(MEMBER_ID, 10);

        // then
        assertThat(result).hasSize(2);

        verify(queryRepairService, never()).repairLatestActive(anyString(), anyInt());
        verify(queryRepairService, never()).repairByIds(anyList());
        verify(persistence, never()).getLastReadSeq(anyString(), anyString());
        verify(cache, never()).updateLastRead(anyString(), anyString(), anyLong());
        verify(cache, never()).updateRecentScore(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("listLatestActive: 인덱스가 없으면 복구 조회 후 영속 저장소의 lastRead 기준으로 active 캐시를 갱신한다")
    void should_repair_latest_active_and_use_persisted_last_read_when_index_missing() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room.getLastMsgCreatedAtMs()).thenReturn(1_000L);
        when(room.hasUnread(1L)).thenReturn(true);

        when(room2.getId()).thenReturn("room-2");
        when(room2.getLastMsgCreatedAtMs()).thenReturn(2_000L);
        when(room2.hasUnread(2L)).thenReturn(false);

        List<ChatRoom> repaired = List.of(room, room2);

        when(cache.listLatestActive(MEMBER_ID, 10))
                .thenReturn(noIndex());
        when(queryRepairService.repairLatestActive(MEMBER_ID, 10))
                .thenReturn(repaired);
        when(persistence.getLastReadSeq("room-1", MEMBER_ID))
                .thenReturn(1L);
        when(persistence.getLastReadSeq("room-2", MEMBER_ID))
                .thenReturn(2L);

        // when
        List<MyChatRoomSummary> result = sut.listLatestActive(MEMBER_ID, 10);

        // then
        assertThat(result).hasSize(2);

        verify(queryRepairService, times(1)).repairLatestActive(MEMBER_ID, 10);
        verify(queryRepairService, never()).repairByIds(anyList());

        verify(cache).updateLastRead("room-1", MEMBER_ID, 1L);
        verify(cache).updateRecentScore(
                "room-1",
                MEMBER_ID,
                ChatRoomActivityScore.calculate(1_000L, true)
        );

        verify(cache).updateLastRead("room-2", MEMBER_ID, 2L);
        verify(cache).updateRecentScore(
                "room-2",
                MEMBER_ID,
                ChatRoomActivityScore.calculate(2_000L, false)
        );
    }

    @Test
    @DisplayName("listLatestActive: 일부 미스 시 미스난 roomId만 repairByIds로 복구하고 hit/repaired Summary를 원래 순서대로 병합한다")
    void should_repair_only_missed_rooms_when_latest_active_partially_misses() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room2.getId()).thenReturn("room-2");
        when(room2.getLastMsgCreatedAtMs()).thenReturn(2_000L);
        when(room2.hasUnread(2L)).thenReturn(false);

        ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                List.of("room-1", "room-2"),
                List.of(room),
                List.of("room-2")
        );

        when(cache.listLatestActive(MEMBER_ID, 10))
                .thenReturn(cached);
        when(cache.getLastMsgSeq("room-1", MEMBER_ID))
                .thenReturn(Optional.of(1L));
        when(queryRepairService.repairByIds(List.of("room-2")))
                .thenReturn(List.of(room2));
        when(persistence.getLastReadSeq("room-2", MEMBER_ID))
                .thenReturn(2L);

        // when
        List<MyChatRoomSummary> result = sut.listLatestActive(MEMBER_ID, 10);

        // then
        assertThat(result).hasSize(2);

        verify(queryRepairService, never()).repairLatestActive(anyString(), anyInt());
        verify(queryRepairService, times(1)).repairByIds(List.of("room-2"));
        verify(cache, never()).updateLastRead("room-1", MEMBER_ID, 1L);
        verify(cache).updateLastRead("room-2", MEMBER_ID, 2L);
    }

    @Test
    @DisplayName("listActiveBefore: 전체 히트 시 lastRead 캐시를 사용해 내 채팅방 Summary 목록을 반환한다")
    void should_return_active_before_from_cache_when_all_hit() {
        // given
        Long score = ChatRoomActivityScore.calculate(1_000L, true);

        when(room.getId()).thenReturn("room-1");

        ChatRoomCacheLookupResult cached = allHit(
                List.of("room-1"),
                List.of(room)
        );

        when(cache.listActiveBefore(MEMBER_ID, "last-room", score, 10))
                .thenReturn(cached);
        when(cache.getLastMsgSeq("room-1", MEMBER_ID))
                .thenReturn(Optional.of(10L));

        // when
        List<MyChatRoomSummary> result = sut.listActiveBefore(
                MEMBER_ID,
                "last-room",
                true,
                1_000L,
                10
        );

        // then
        assertThat(result).hasSize(1);

        verify(queryRepairService, never()).repairActiveBefore(anyString(), anyString(), anyLong(), anyInt());
        verify(queryRepairService, never()).repairByIds(anyList());
    }

    @Test
    @DisplayName("listActiveBefore: 인덱스가 없으면 repairActiveBefore로 복구 조회하고 lastRead 기준으로 active 캐시를 갱신한다")
    void should_repair_active_before_when_index_missing() {
        // given
        Long score = ChatRoomActivityScore.calculate(1_000L, true);

        when(room.getId()).thenReturn("room-1");
        when(room.getLastMsgCreatedAtMs()).thenReturn(1_000L);
        when(room.hasUnread(10L)).thenReturn(false);

        when(cache.listActiveBefore(MEMBER_ID, "last-room", score, 10))
                .thenReturn(noIndex());
        when(queryRepairService.repairActiveBefore(MEMBER_ID, "last-room", score, 10))
                .thenReturn(List.of(room));
        when(persistence.getLastReadSeq("room-1", MEMBER_ID))
                .thenReturn(10L);

        // when
        List<MyChatRoomSummary> result = sut.listActiveBefore(
                MEMBER_ID,
                "last-room",
                true,
                1_000L,
                10
        );

        // then
        assertThat(result).hasSize(1);

        verify(queryRepairService, times(1))
                .repairActiveBefore(MEMBER_ID, "last-room", score, 10);
        verify(queryRepairService, never()).repairByIds(anyList());
        verify(cache, times(1)).updateLastRead("room-1", MEMBER_ID, 10L);
    }

    @Test
    @DisplayName("listActiveBefore: 일부 미스 시 미스난 roomId만 repairByIds로 복구하고 원래 순서대로 병합한다")
    void should_repair_only_missed_rooms_when_active_before_partially_misses() {
        // given
        Long score = ChatRoomActivityScore.calculate(1_000L, true);

        when(room.getId()).thenReturn("room-1");
        when(room2.getId()).thenReturn("room-2");
        when(room2.getLastMsgCreatedAtMs()).thenReturn(2_000L);
        when(room2.hasUnread(2L)).thenReturn(false);

        ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                List.of("room-1", "room-2"),
                List.of(room),
                List.of("room-2")
        );

        when(cache.listActiveBefore(MEMBER_ID, "last-room", score, 10))
                .thenReturn(cached);
        when(cache.getLastMsgSeq("room-1", MEMBER_ID))
                .thenReturn(Optional.of(10L));
        when(queryRepairService.repairByIds(List.of("room-2")))
                .thenReturn(List.of(room2));
        when(persistence.getLastReadSeq("room-2", MEMBER_ID))
                .thenReturn(2L);

        // when
        List<MyChatRoomSummary> result = sut.listActiveBefore(
                MEMBER_ID,
                "last-room",
                true,
                1_000L,
                10
        );

        // then
        assertThat(result).hasSize(2);

        verify(queryRepairService, never()).repairActiveBefore(anyString(), anyString(), anyLong(), anyInt());
        verify(queryRepairService, times(1)).repairByIds(List.of("room-2"));
        verify(cache).updateLastRead("room-2", MEMBER_ID, 2L);
    }

    @Test
    @DisplayName("existsByTitle: 캐시 히트 시 캐시 결과를 반환한다")
    void should_return_exists_by_title_from_cache_when_cache_hits() {
        // given
        when(cache.existsByTitle("title"))
                .thenReturn(Optional.of(true));

        // when
        boolean result = sut.existsByTitle("title");

        // then
        assertThat(result).isTrue();

        verify(persistence, never()).existsByTitle(anyString());
    }

    @Test
    @DisplayName("existsByTitle: 캐시 미스 시 영속 저장소에서 제목 존재 여부를 조회한다")
    void should_query_persistence_when_exists_by_title_cache_misses() {
        // given
        when(cache.existsByTitle("title"))
                .thenReturn(Optional.empty());
        when(persistence.existsByTitle("title"))
                .thenReturn(false);

        // when
        boolean result = sut.existsByTitle("title");

        // then
        assertThat(result).isFalse();

        verify(persistence, times(1)).existsByTitle("title");
    }

    @Test
    @DisplayName("findActive: active 캐시 갱신 실패 시에도 조회 Summary를 반환한다")
    void should_return_summary_even_when_active_cache_refresh_fails() {
        // given
        when(room.getId()).thenReturn(ROOM_ID);

        when(cache.findById(ROOM_ID))
                .thenReturn(Optional.of(room));
        when(cache.getLastMsgSeq(ROOM_ID, MEMBER_ID))
                .thenReturn(Optional.empty());
        when(persistence.getLastReadSeq(ROOM_ID, MEMBER_ID))
                .thenReturn(10L);

        doThrow(new RuntimeException("redis failed"))
                .when(cache)
                .updateLastRead(ROOM_ID, MEMBER_ID, 10L);

        // when
        MyChatRoomSummary result = sut.findActive(ROOM_ID, MEMBER_ID);

        // then
        assertThat(result).isNotNull();

        verify(cache, times(1)).updateLastRead(ROOM_ID, MEMBER_ID, 10L);
        verify(cache, never()).updateRecentScore(anyString(), anyString(), anyLong());
    }

    private ChatRoomCacheLookupResult noIndex() {
        return new ChatRoomCacheLookupResult(
                List.of(),
                List.of(),
                List.of()
        );
    }

    private ChatRoomCacheLookupResult allHit(List<String> orderedIds, List<ChatRoom> hits) {
        return new ChatRoomCacheLookupResult(
                orderedIds,
                hits,
                List.of()
        );
    }
}