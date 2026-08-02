package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.application.service.query.ListMyChatRoomsQuery;
import org.example.chat.chatroom.application.service.query.ListPopularChatRoomsQuery;
import org.example.chat.chatroom.application.service.result.ChatRoomCacheLookupResult;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.exception.ChatRoomNotFoundException;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.redisson.singleflight.SingleFlight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ChatRoomQueryRepairServiceUnitTest {

    @Mock
    private ChatRoomCachePort cache;

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private SingleFlight singleFlight;

    @InjectMocks
    private ChatRoomQueryRepairService sut;


    @Mock
    private ChatRoom room;

    @Mock
    private ChatRoom room2;

    @Nested
    @DisplayName("repairRoom")
    class RepairRoomTest {

        @Test
        @DisplayName("락 내부 double-check에서 캐시 히트 시 영속 저장소를 조회하지 않고 캐시된 채팅방을 반환한다")
        void should_return_cached_room_without_persistence_when_double_check_hits() {
            // given
            givenLockExecutorRunsSupplier();

            when(cache.findById("room-1"))
                    .thenReturn(Optional.of(room));

            // when
            ChatRoom result = sut.repairRoom("room-1");

            // then
            assertThat(result).isSameAs(room);

            verify(singleFlight).execute(
                    anyString(),
                    any()
            );
            verify(persistence, never()).findByIdWithLatestMessage(anyString());
            verify(cache, never()).warmUp(any());
        }

        @Test
        @DisplayName("캐시 미스 시 영속 저장소에서 채팅방을 조회하고 캐시를 warm-up 한다")
        void should_load_room_from_persistence_and_warm_up_when_cache_misses() {
            // given
            givenLockExecutorRunsSupplier();

            when(cache.findById("room-1"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-1"))
                    .thenReturn(Optional.of(room));

            // when
            ChatRoom result = sut.repairRoom("room-1");

            // then
            assertThat(result).isSameAs(room);

            verify(persistence, times(1)).findByIdWithLatestMessage("room-1");
            verify(cache, times(1)).warmUp(room);
        }

        @Test
        @DisplayName("캐시 warm-up에 실패해도 영속 저장소에서 조회한 채팅방을 반환한다")
        void should_return_room_even_when_warm_up_fails() {
            // given
            givenLockExecutorRunsSupplier();

            when(cache.findById("room-1"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-1"))
                    .thenReturn(Optional.of(room));
            doThrow(new RuntimeException("redis failed"))
                    .when(cache)
                    .warmUp(room);

            // when
            ChatRoom result = sut.repairRoom("room-1");

            // then
            assertThat(result).isSameAs(room);

            verify(persistence, times(1)).findByIdWithLatestMessage("room-1");
            verify(cache, times(1)).warmUp(room);
        }

        @Test
        @DisplayName("영속 저장소에도 채팅방이 없으면 ChatRoomNotFoundException을 던진다")
        void should_throw_not_found_exception_when_room_does_not_exist() {
            // given
            givenLockExecutorRunsSupplier();

            when(cache.findById("room-1"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-1"))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sut.repairRoom("room-1"))
                    .isInstanceOf(ChatRoomNotFoundException.class);

            verify(cache, never()).warmUp(any());
        }
    }

    @Nested
    @DisplayName("repairRoomsByIds")
    class RepairRoomsByIdsTest {

        @Test
        @DisplayName("미스난 roomId만 repairRoom 흐름으로 복구한다")
        void should_repair_only_missed_room_ids() {
            // given
            givenLockExecutorRunsSupplier();

            when(cache.findById("room-1"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-1"))
                    .thenReturn(Optional.of(room));

            when(cache.findById("room-2"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-2"))
                    .thenReturn(Optional.of(room2));

            // when
            List<ChatRoom> result = sut.repairRoomsByIds(List.of("room-1", "room-2"));

            // then
            assertThat(result).containsExactly(room, room2);

            verify(persistence).findByIdWithLatestMessage("room-1");
            verify(persistence).findByIdWithLatestMessage("room-2");
            verify(cache).warmUp(room);
            verify(cache).warmUp(room2);
        }

        @Test
        @DisplayName("DB에도 없는 roomId는 건너뛰고 복구 가능한 채팅방만 반환한다")
        void should_skip_not_found_room_when_repair_by_ids() {
            // given
            givenLockExecutorRunsSupplier();

            when(cache.findById("room-1"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-1"))
                    .thenReturn(Optional.of(room));

            when(cache.findById("dead-room"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("dead-room"))
                    .thenReturn(Optional.empty());

            // when
            List<ChatRoom> result = sut.repairRoomsByIds(List.of("room-1", "dead-room"));

            // then
            assertThat(result).containsExactly(room);

            verify(cache).warmUp(room);
            verify(cache, never()).warmUp(room2);
        }

        @Test
        @DisplayName("roomId 목록이 null이면 빈 목록을 반환한다")
        void should_return_empty_list_when_room_ids_is_null() {
            // when
            List<ChatRoom> result = sut.repairRoomsByIds(null);

            // then
            assertThat(result).isEmpty();

            verify(singleFlight, never()).execute(anyString(), any());
        }

        @Test
        @DisplayName("roomId 목록이 비어 있으면 빈 목록을 반환한다")
        void should_return_empty_list_when_room_ids_is_empty() {
            // when
            List<ChatRoom> result = sut.repairRoomsByIds(List.of());

            // then
            assertThat(result).isEmpty();

            verify(singleFlight, never()).execute(anyString(), any());
        }
    }

    @Nested
    @DisplayName("repairPopularRooms")
    class RepairPopularRoomsTest {

        @Test
        @DisplayName("락 내부 double-check에서 전체 히트 시 영속 저장소를 조회하지 않고 인기 채팅방 목록을 반환한다")
        void should_return_cached_popular_rooms_without_persistence_when_double_check_all_hits() {
            // given
            givenLockExecutorRunsSupplier();

            ChatRoomCacheLookupResult cached = allHit(List.of("room-1"), List.of(room));

            when(cache.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(cached);

            // when
            List<ChatRoom> result = sut.repairPopularRooms(ChatRoomCategory.FREE, 10);

            // then
            assertThat(result).containsExactly(room);

            verify(persistence, never()).listPopularRooms(any(), anyInt());
            verify(cache, never()).warmUpList(anyList());
        }

        @Test
        @DisplayName("인덱스가 없으면 영속 저장소에서 인기 채팅방 목록을 조회하고 popularity score로 warm-up 한다")
        void should_load_popular_rooms_from_persistence_and_warm_up_when_index_missing() {
            // given
            givenLockExecutorRunsSupplier();

            List<ChatRoom> stored = List.of(room, room2);

            when(cache.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(noIndex());
            when(persistence.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(stored);

            // when
            List<ChatRoom> result = sut.repairPopularRooms(ChatRoomCategory.FREE, 10);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(persistence, times(1)).listPopularRooms(ChatRoomCategory.FREE, 10);
            verify(cache, times(1)).warmUpList(eq(stored));
        }

        @Test
        @DisplayName("부분 미스면 미스난 roomId만 복구하고 원래 순서대로 반환한다")
        void should_repair_only_missed_rooms_when_partially_misses() {
            // given
            givenLockExecutorRunsSupplier();

            when(room.getId()).thenReturn("room-1");
            when(room2.getId()).thenReturn("room-2");

            ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                    List.of("room-1", "room-2"),
                    List.of(room),
                    List.of("room-2")
            );

            when(cache.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(cached);
            when(cache.findById("room-2"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-2"))
                    .thenReturn(Optional.of(room2));

            // when
            List<ChatRoom> result = sut.repairPopularRooms(ChatRoomCategory.FREE, 10);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(persistence, never()).listPopularRooms(any(), anyInt());
            verify(persistence).findByIdWithLatestMessage("room-2");
            verify(cache).warmUp(room2);
        }

        @Test
        @DisplayName("영속 저장소 조회 결과가 비어 있으면 빈 목록을 반환하고 warm-up 하지 않는다")
        void should_return_empty_list_and_not_warm_up_when_not_found() {
            // given
            givenLockExecutorRunsSupplier();

            when(cache.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(noIndex());
            when(persistence.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(List.of());

            // when
            List<ChatRoom> result = sut.repairPopularRooms(ChatRoomCategory.FREE, 10);

            // then
            assertThat(result).isEmpty();

            verify(cache, never()).warmUpList(anyList());
        }

        @Test
        @DisplayName("목록 warm-up에 실패해도 영속 저장소에서 조회한 목록을 반환한다")
        void should_return_stored_list_even_when_warm_up_list_fails() {
            // given
            givenLockExecutorRunsSupplier();

            List<ChatRoom> stored = List.of(room, room2);

            when(cache.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(noIndex());
            when(persistence.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(stored);
            doThrow(new RuntimeException("redis failed"))
                    .when(cache)
                    .warmUpList(anyList());

            // when
            List<ChatRoom> result = sut.repairPopularRooms(ChatRoomCategory.FREE, 10);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(cache, times(1)).warmUpList(eq(stored));
        }
    }

    @Nested
    @DisplayName("repairPopularRoomsAfter")
    class RepairPopularRoomsAfterTest {

        @Test
        @DisplayName("인덱스가 없으면 영속 저장소에서 다음 인기 채팅방 목록을 조회하고 warm-up 한다")
        void should_load_popular_rooms_after_from_persistence_and_warm_up_when_index_missing() {
            // given
            givenLockExecutorRunsSupplier();

            List<ChatRoom> stored = List.of(room, room2);
            ListPopularChatRoomsQuery query = popularRoomsAfterQuery();

            when(cache.listPopularRoomsAfter(ChatRoomCategory.FREE, "last-room", 100L, 10))
                    .thenReturn(noIndex());
            when(persistence.listPopularRoomsAfter(ChatRoomCategory.FREE, "last-room", 100L, 10))
                    .thenReturn(stored);

            // when
            List<ChatRoom> result = sut.repairPopularRoomsAfter(query);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(persistence, times(1))
                    .listPopularRoomsAfter(ChatRoomCategory.FREE, "last-room", 100L, 10);
            verify(cache, times(1)).warmUpList(eq(stored));
        }

        @Test
        @DisplayName("부분 미스면 미스난 roomId만 복구하고 원래 순서대로 반환한다")
        void should_repair_only_missed_rooms_when_partially_misses() {
            // given
            givenLockExecutorRunsSupplier();

            when(room.getId()).thenReturn("room-1");
            when(room2.getId()).thenReturn("room-2");

            ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                    List.of("room-1", "room-2"),
                    List.of(room),
                    List.of("room-2")
            );

            ListPopularChatRoomsQuery query = popularRoomsAfterQuery();

            when(cache.listPopularRoomsAfter(ChatRoomCategory.FREE, "last-room", 100L, 10))
                    .thenReturn(cached);
            when(cache.findById("room-2"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-2"))
                    .thenReturn(Optional.of(room2));

            // when
            List<ChatRoom> result = sut.repairPopularRoomsAfter(query);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(persistence, never()).listPopularRoomsAfter(any(), anyString(), anyLong(), anyInt());
            verify(persistence).findByIdWithLatestMessage("room-2");
            verify(cache).warmUp(room2);
        }
    }

    @Nested
    @DisplayName("repairMyRooms")
    class RepairMyRoomsTest {

        @Test
        @DisplayName("락 내부 double-check에서 전체 히트 시 영속 저장소를 조회하지 않고 최근 활성 채팅방 목록을 반환한다")
        void should_return_cached_my_rooms_without_persistence_when_double_check_all_hits() {
            // given
            givenLockExecutorRunsSupplier();

            ChatRoomCacheLookupResult cached = allHit(List.of("room-1"), List.of(room));

            when(cache.listLatestActiveRooms("member-1", 10))
                    .thenReturn(cached);

            // when
            List<ChatRoom> result = sut.repairMyRooms("member-1", 10);

            // then
            assertThat(result).containsExactly(room);

            verify(persistence, never()).listLatestActiveRooms(anyString(), anyInt());
            verify(cache, never()).warmUpList(anyList());
        }

        @Test
        @DisplayName("인덱스가 없으면 영속 저장소에서 최근 활성 채팅방 목록을 조회하고 warm-up 한다")
        void should_load_my_rooms_from_persistence_and_warm_up_when_index_missing() {
            // given
            givenLockExecutorRunsSupplier();

            List<ChatRoom> stored = List.of(room, room2);

            when(cache.listLatestActiveRooms("member-1", 10))
                    .thenReturn(noIndex());
            when(persistence.listLatestActiveRooms("member-1", 10))
                    .thenReturn(stored);

            // when
            List<ChatRoom> result = sut.repairMyRooms("member-1", 10);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(persistence, times(1)).listLatestActiveRooms("member-1", 10);
            verify(cache, times(1)).warmUpList(eq(stored));
        }

        @Test
        @DisplayName("부분 미스면 미스난 roomId만 복구하고 원래 순서대로 반환한다")
        void should_repair_only_missed_rooms_when_partially_misses() {
            // given
            givenLockExecutorRunsSupplier();

            when(room.getId()).thenReturn("room-1");
            when(room2.getId()).thenReturn("room-2");

            ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                    List.of("room-1", "room-2"),
                    List.of(room),
                    List.of("room-2")
            );

            when(cache.listLatestActiveRooms("member-1", 10))
                    .thenReturn(cached);
            when(cache.findById("room-2"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-2"))
                    .thenReturn(Optional.of(room2));

            // when
            List<ChatRoom> result = sut.repairMyRooms("member-1", 10);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(persistence, never()).listLatestActiveRooms(anyString(), anyInt());
            verify(persistence).findByIdWithLatestMessage("room-2");
            verify(cache).warmUp(room2);
        }
    }

    @Nested
    @DisplayName("repairMyRoomsBefore")
    class RepairMyRoomsBeforeTest {

        @Test
        @DisplayName("인덱스가 없으면 영속 저장소에서 커서 이전 활성 채팅방 목록을 조회하고 warm-up 한다")
        void should_load_my_rooms_before_from_persistence_and_warm_up_when_index_missing() {
            // given
            givenLockExecutorRunsSupplier();

            List<ChatRoom> stored = List.of(room, room2);
            ListMyChatRoomsQuery query = myRoomsBeforeQuery();

            when(cache.listActiveRoomsBefore("member-1", "last-room", 1234L, 10))
                    .thenReturn(noIndex());
            when(persistence.listActiveRoomsBefore("member-1", "last-room", 1234L, 10))
                    .thenReturn(stored);

            // when
            List<ChatRoom> result = sut.repairMyRoomsBefore(query, 1234L);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(persistence, times(1))
                    .listActiveRoomsBefore("member-1", "last-room", 1234L, 10);
            verify(cache, times(1)).warmUpList(eq(stored));
        }

        @Test
        @DisplayName("부분 미스면 미스난 roomId만 복구하고 원래 순서대로 반환한다")
        void should_repair_only_missed_rooms_when_partially_misses() {
            // given
            givenLockExecutorRunsSupplier();

            when(room.getId()).thenReturn("room-1");
            when(room2.getId()).thenReturn("room-2");

            ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                    List.of("room-1", "room-2"),
                    List.of(room),
                    List.of("room-2")
            );

            ListMyChatRoomsQuery query = myRoomsBeforeQuery();

            when(cache.listActiveRoomsBefore("member-1", "last-room", 1234L, 10))
                    .thenReturn(cached);
            when(cache.findById("room-2"))
                    .thenReturn(Optional.empty());
            when(persistence.findByIdWithLatestMessage("room-2"))
                    .thenReturn(Optional.of(room2));

            // when
            List<ChatRoom> result = sut.repairMyRoomsBefore(query, 1234L);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(persistence, never()).listActiveRoomsBefore(anyString(), anyString(), anyLong(), anyInt());
            verify(persistence).findByIdWithLatestMessage("room-2");
            verify(cache).warmUp(room2);
        }
    }

    private void givenLockExecutorRunsSupplier() {
        when(singleFlight.execute(
                anyString(),
                any()
        )).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    private ListPopularChatRoomsQuery popularRoomsAfterQuery() {
        return ListPopularChatRoomsQuery.nextPage(
                ChatRoomCategory.FREE,
                "last-room",
                100L,
                10
        );
    }

    private ListMyChatRoomsQuery myRoomsBeforeQuery() {
        return ListMyChatRoomsQuery.nextPage(
                "member-1",
                "last-room",
                true,
                1234L,
                10
        );
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