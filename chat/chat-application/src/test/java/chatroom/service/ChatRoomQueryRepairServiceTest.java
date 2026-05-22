package chatroom.service;

import org.example.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chatroom.application.service.ChatRoomQueryRepairService;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.common.redis.DistributedLockExecutor;
import org.example.common.redis.DistributedLockPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomQueryRepairServiceTest {

    @Mock
    private ChatRoomCachePort cache;

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private DistributedLockExecutor distributedLockExecutor;

    @InjectMocks
    private ChatRoomQueryRepairService sut;

    private final DistributedLockPolicy distributedLockPolicy = DistributedLockPolicy.CACHE_WARM_UP;
    private ChatRoom room, room2;

    @BeforeEach
    void setUp() {
        room = mock(ChatRoom.class);
        room2 = mock(ChatRoom.class);

        when(distributedLockExecutor.execute(
                anyString(),
                any(),
                eq(distributedLockPolicy)
        )).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    @Test
    void should_return_cached_room_without_persistence_when_repair_find_by_id_double_check_hits() {
        // given
        when(cache.findById("room-1"))
                .thenReturn(Optional.of(room));

        // when
        ChatRoom result = sut.repairFindById("room-1");

        // then
        assertThat(result).isSameAs(room);

        verify(distributedLockExecutor).execute(
                anyString(),
                any(),
                eq(distributedLockPolicy)
        );
        verify(persistence, never()).findByIdWithLatest(anyString());
        verify(cache, never()).warmUp(any());
    }

    @Test
    void should_load_room_from_persistence_and_warm_up_when_repair_find_by_id_cache_misses() {
        // given
        when(cache.findById("room-1"))
                .thenReturn(Optional.empty());
        when(persistence.findByIdWithLatest("room-1"))
                .thenReturn(Optional.of(room));

        // when
        ChatRoom result = sut.repairFindById("room-1");

        // then
        assertThat(result).isSameAs(room);

        verify(persistence, times(1)).findByIdWithLatest("room-1");
        verify(cache, times(1)).warmUp(room);
    }

    @Test
    void should_return_room_even_when_warm_up_fails() {
        // given
        when(cache.findById("room-1"))
                .thenReturn(Optional.empty());
        when(persistence.findByIdWithLatest("room-1"))
                .thenReturn(Optional.of(room));
        doThrow(new RuntimeException("redis failed"))
                .when(cache)
                .warmUp(room);

        // when
        ChatRoom result = sut.repairFindById("room-1");

        // then
        assertThat(result).isSameAs(room);

        verify(persistence, times(1)).findByIdWithLatest("room-1");
        verify(cache, times(1)).warmUp(room);
    }

    @Test
    void should_throw_not_found_exception_when_room_does_not_exist() {
        // given
        when(cache.findById("room-1"))
                .thenReturn(Optional.empty());
        when(persistence.findByIdWithLatest("room-1"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.repairFindById("room-1"))
                .isInstanceOf(ChatRoomNotFoundException.class);

        verify(cache, never()).warmUp(any());
    }

    @Test
    void should_return_cached_most_popular_without_persistence_when_double_check_hits() {
        // given
        List<ChatRoom> cached = List.of(room);

        when(cache.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(cached);

        // when
        List<ChatRoom> result = sut.repairMostPopular(ChatRoomCategory.FREE, 10);

        // then
        assertThat(result).containsExactly(room);

        verify(persistence, never()).listMostPopular(any(), anyInt());
        verify(cache, never()).warmUpList(anyList(), anyMap());
    }

    @Test
    void should_load_most_popular_from_persistence_and_warm_up_when_cache_misses() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room.getPopularity()).thenReturn(10.0);

        when(room2.getId()).thenReturn("room-2");
        when(room2.getPopularity()).thenReturn(20.0);

        List<ChatRoom> stored = List.of(room, room2);

        when(cache.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(List.of());
        when(persistence.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(stored);

        // when
        List<ChatRoom> result = sut.repairMostPopular(ChatRoomCategory.FREE, 10);

        // then
        assertThat(result).containsExactly(room, room2);

        ArgumentCaptor<Map<String, Double>> popularityCaptor = ArgumentCaptor.forClass(Map.class);

        verify(persistence, times(1)).listMostPopular(ChatRoomCategory.FREE, 10);
        verify(cache, times(1)).warmUpList(eq(stored), popularityCaptor.capture());

        assertThat(popularityCaptor.getValue())
                .containsEntry("room-1", 10.0)
                .containsEntry("room-2", 20.0);
    }

    @Test
    void should_return_empty_list_and_not_warm_up_when_most_popular_not_found() {
        // given
        when(cache.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(List.of());
        when(persistence.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(List.of());

        // when
        List<ChatRoom> result = sut.repairMostPopular(ChatRoomCategory.FREE, 10);

        // then
        assertThat(result).isEmpty();

        verify(cache, never()).warmUpList(anyList(), anyMap());
    }

    @Test
    void should_load_next_popular_from_persistence_and_warm_up_when_cache_misses() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room.getPopularity()).thenReturn(10.0);

        when(room2.getId()).thenReturn("room-2");
        when(room2.getPopularity()).thenReturn(20.0);

        List<ChatRoom> stored = List.of(room, room2);

        when(cache.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10))
                .thenReturn(List.of());
        when(persistence.listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10))
                .thenReturn(stored);

        // when
        List<ChatRoom> result = sut.repairNextPopular(
                ChatRoomCategory.FREE,
                "last-room",
                100L,
                10
        );

        // then
        assertThat(result).containsExactly(room, room2);

        verify(persistence, times(1))
                .listNextPopular(ChatRoomCategory.FREE, "last-room", 100L, 10);
        verify(cache, times(1))
                .warmUpList(eq(stored), anyMap());
    }

    @Test
    void should_return_cached_latest_active_without_persistence_when_double_check_hits() {
        // given
        List<ChatRoom> cached = List.of(room);

        when(cache.listLatestActive("member-1", 10))
                .thenReturn(cached);

        // when
        List<ChatRoom> result = sut.repairLatestActive("member-1", 10);

        // then
        assertThat(result).containsExactly(room);

        verify(persistence, never()).listLatestActive(anyString(), anyInt());
        verify(cache, never()).warmUpList(anyList(), anyMap());
    }

    @Test
    void should_load_latest_active_from_persistence_and_warm_up_when_cache_misses() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room.getPopularity()).thenReturn(10.0);

        when(room2.getId()).thenReturn("room-2");
        when(room2.getPopularity()).thenReturn(20.0);

        List<ChatRoom> stored = List.of(room, room2);

        when(cache.listLatestActive("member-1", 10))
                .thenReturn(List.of());
        when(persistence.listLatestActive("member-1", 10))
                .thenReturn(stored);

        // when
        List<ChatRoom> result = sut.repairLatestActive("member-1", 10);

        // then
        assertThat(result).containsExactly(room, room2);

        verify(persistence, times(1)).listLatestActive("member-1", 10);
        verify(cache, times(1)).warmUpList(eq(stored), anyMap());
    }

    @Test
    void should_load_active_before_from_persistence_and_warm_up_when_cache_misses() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room.getPopularity()).thenReturn(10.0);

        when(room2.getId()).thenReturn("room-2");
        when(room2.getPopularity()).thenReturn(20.0);

        List<ChatRoom> stored = List.of(room, room2);

        when(cache.listActiveBefore("member-1", "last-room", 1234L, 10))
                .thenReturn(List.of());
        when(persistence.listActiveBefore("member-1", "last-room", 1234L, 10))
                .thenReturn(stored);

        // when
        List<ChatRoom> result = sut.repairActiveBefore(
                "member-1",
                "last-room",
                1234L,
                10
        );

        // then
        assertThat(result).containsExactly(room, room2);

        verify(persistence, times(1))
                .listActiveBefore("member-1", "last-room", 1234L, 10);
        verify(cache, times(1))
                .warmUpList(eq(stored), anyMap());
    }

    @Test
    void should_return_stored_list_even_when_warm_up_list_fails() {
        // given
        when(room.getId()).thenReturn("room-1");
        when(room.getPopularity()).thenReturn(10.0);

        when(room2.getId()).thenReturn("room-2");
        when(room2.getPopularity()).thenReturn(20.0);

        List<ChatRoom> stored = List.of(room, room2);

        when(cache.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(List.of());
        when(persistence.listMostPopular(ChatRoomCategory.FREE, 10))
                .thenReturn(stored);
        doThrow(new RuntimeException("redis failed"))
                .when(cache)
                .warmUpList(anyList(), anyMap());

        // when
        List<ChatRoom> result = sut.repairMostPopular(ChatRoomCategory.FREE, 10);

        // then
        assertThat(result).containsExactly(room, room2);

        verify(cache, times(1)).warmUpList(eq(stored), anyMap());
    }
}