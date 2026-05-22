package chatroom.service;

import org.example.chatroom.application.dto.MyChatRoomResponse;
import org.example.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chatroom.application.service.ChatRoomActivityScore;
import org.example.chatroom.application.service.ChatRoomQueryRepairService;
import org.example.chatroom.application.service.ChatRoomQueryService;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
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
    private final String MEMBER_ID = "member-1";

    @Test
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
    void should_return_active_response_without_repair_when_room_and_last_read_cache_hit() {
        // given
        when(room.getId()).thenReturn(ROOM_ID);

        when(cache.findById(ROOM_ID))
                .thenReturn(Optional.of(room));
        when(cache.getLastMsgSeq(ROOM_ID, MEMBER_ID))
                .thenReturn(Optional.of(10L));

        // when
        MyChatRoomResponse result = sut.findActive(ROOM_ID, MEMBER_ID);

        // then
        assertThat(result).isNotNull();

        verify(queryRepairService, never()).repairFindById(anyString());
        verify(persistence, never()).getLastReadSeq(anyString(), anyString());
        verify(cache, never()).updateLastRead(anyString(), anyString(), anyLong());
        verify(cache, never()).updateRecentScore(anyString(), anyString(), anyLong());
    }

    @Test
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
        MyChatRoomResponse result = sut.findActive(ROOM_ID, MEMBER_ID);

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
        MyChatRoomResponse result = sut.findActive(ROOM_ID, MEMBER_ID);

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
    void should_return_cached_most_popular_when_cache_hits() {
        // given
        List<ChatRoom> cached = List.of(room);

        when(cache.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(cached);

        // when
        List<ChatRoom> result = sut.listMostPopular(ChatRoomCategory.FREE, 10);

        // then
        assertThat(result).containsExactly(room);

        verify(queryRepairService, never()).repairMostPopular(any(), anyInt());
    }

    @Test
    void should_repair_most_popular_when_cache_misses() {
        // given
        List<ChatRoom> repaired = List.of(room, room2);

        when(cache.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(List.of());
        when(queryRepairService.repairMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(repaired);

        // when
        List<ChatRoom> result = sut.listMostPopular(ChatRoomCategory.FREE, 10);

        // then
        assertThat(result).containsExactly(room, room2);

        verify(queryRepairService, times(1)).repairMostPopular(ChatRoomCategory.FREE, 10);
    }

    @Test
    void should_return_cached_next_popular_when_cache_hits() {
        // given
        List<ChatRoom> cached = List.of(room);

        when(cache.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10))
                .thenReturn(cached);

        // when
        List<ChatRoom> result = sut.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10);

        // then
        assertThat(result).containsExactly(room);

        verify(queryRepairService, never()).repairNextPopular(any(), anyString(), anyLong(), anyInt());
    }

    @Test
    void should_repair_next_popular_when_cache_misses() {
        // given
        List<ChatRoom> repaired = List.of(room, room2);

        when(cache.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10))
                .thenReturn(List.of());
        when(queryRepairService.repairNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10))
                .thenReturn(repaired);

        // when
        List<ChatRoom> result = sut.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10);

        // then
        assertThat(result).containsExactly(room, room2);

        verify(queryRepairService, times(1))
                .repairNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10);
    }

    @Test
    void should_return_latest_active_from_cache_and_use_last_read_cache() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room2.getId()).thenReturn("room-2");

        List<ChatRoom> cached = List.of(room, room2);

        when(cache.listLatestActive(MEMBER_ID, 10))
                .thenReturn(cached);
        when(cache.getLastMsgSeq("room-1", MEMBER_ID))
                .thenReturn(Optional.of(1L));
        when(cache.getLastMsgSeq("room-2", MEMBER_ID))
                .thenReturn(Optional.of(2L));

        // when
        List<MyChatRoomResponse> result = sut.listLatestActive(MEMBER_ID, 10);

        // then
        assertThat(result).hasSize(2);

        verify(queryRepairService, never()).repairLatestActive(anyString(), anyInt());
        verify(persistence, never()).getLastReadSeq(anyString(), anyString());
        verify(cache, never()).updateLastRead(anyString(), anyString(), anyLong());
        verify(cache, never()).updateRecentScore(anyString(), anyString(), anyLong());
    }

    @Test
    void should_repair_latest_active_and_use_persisted_last_read_when_cache_misses() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room.getLastMsgCreatedAtMs()).thenReturn(1_000L);
        when(room.hasUnread(1L)).thenReturn(true);

        when(room2.getId()).thenReturn("room-2");
        when(room2.getLastMsgCreatedAtMs()).thenReturn(2_000L);
        when(room2.hasUnread(2L)).thenReturn(false);

        List<ChatRoom> repaired = List.of(room, room2);

        when(cache.listLatestActive(MEMBER_ID, 10))
                .thenReturn(List.of());
        when(queryRepairService.repairLatestActive(MEMBER_ID, 10))
                .thenReturn(repaired);
        when(persistence.getLastReadSeq("room-1", MEMBER_ID))
                .thenReturn(1L);
        when(persistence.getLastReadSeq("room-2", MEMBER_ID))
                .thenReturn(2L);

        // when
        List<MyChatRoomResponse> result = sut.listLatestActive(MEMBER_ID, 10);

        // then
        assertThat(result).hasSize(2);

        verify(queryRepairService, times(1)).repairLatestActive(MEMBER_ID, 10);

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
    void should_repair_active_before_when_cache_misses() {
        // given
        Long score = ChatRoomActivityScore.calculate(1_000L, true);

        when(room.getId()).thenReturn("room-1");
        when(room.getLastMsgCreatedAtMs()).thenReturn(1_000L);
        when(room.hasUnread(10L)).thenReturn(false);

        when(cache.listActiveBefore(MEMBER_ID, "last-room", score, 10))
                .thenReturn(List.of());
        when(queryRepairService.repairActiveBefore(MEMBER_ID, "last-room", score, 10))
                .thenReturn(List.of(room));
        when(persistence.getLastReadSeq("room-1", MEMBER_ID))
                .thenReturn(10L);

        // when
        List<MyChatRoomResponse> result = sut.listActiveBefore(
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
        verify(cache, times(1)).updateLastRead("room-1", MEMBER_ID, 10L);
    }

    @Test
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
    void should_return_response_even_when_active_cache_refresh_fails() {
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
        MyChatRoomResponse result = sut.findActive(ROOM_ID, MEMBER_ID);

        // then
        assertThat(result).isNotNull();

        verify(cache, times(1)).updateLastRead(ROOM_ID, MEMBER_ID, 10L);
        verify(cache, never()).updateRecentScore(anyString(), anyString(), anyLong());
    }
}
