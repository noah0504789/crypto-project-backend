package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.application.service.query.GetMyChatRoomQuery;
import org.example.chat.chatroom.application.service.query.ListMyChatRoomsQuery;
import org.example.chat.chatroom.application.service.query.ListPopularChatRoomsQuery;
import org.example.chat.chatroom.application.service.result.ChatRoomCacheLookupResult;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.service.result.MyChatRoomSummary;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    private ChatRoom room;

    @Mock
    private ChatRoom room2;

    private final String ROOM_ID = "room-1";
    private final String ROOM_ID_2 = "room-2";
    private final String MEMBER_ID = "member-1";

    @Nested
    @DisplayName("getRoom")
    class GetRoomTest {

        @Test
        @DisplayName("캐시 히트 시 캐시된 채팅방을 반환한다")
        void should_return_cached_room_when_cache_hits() {
            // given
            when(cache.findById(ROOM_ID))
                    .thenReturn(Optional.of(room));

            // when
            ChatRoom result = sut.getRoom(ROOM_ID);

            // then
            assertThat(result).isSameAs(room);

            verify(queryRepairService, never()).repairRoom(anyString());
        }

        @Test
        @DisplayName("캐시 미스 시 repairRoom으로 복구 조회한다")
        void should_repair_room_when_cache_misses() {
            // given
            when(cache.findById(ROOM_ID))
                    .thenReturn(Optional.empty());
            when(queryRepairService.repairRoom(ROOM_ID))
                    .thenReturn(room);

            // when
            ChatRoom result = sut.getRoom(ROOM_ID);

            // then
            assertThat(result).isSameAs(room);

            verify(queryRepairService, times(1)).repairRoom(ROOM_ID);
        }
    }

    @Nested
    @DisplayName("getMyRoom")
    class GetMyRoomTest {

        @Test
        @DisplayName("채팅방 캐시와 lastRead 캐시가 모두 히트하면 복구 조회 없이 Summary를 반환한다")
        void should_return_my_room_summary_without_repair_when_room_and_last_read_cache_hit() {
            // given
            GetMyChatRoomQuery query = getMyChatRoomQuery();

            when(room.getId()).thenReturn(ROOM_ID);

            when(cache.findById(ROOM_ID))
                    .thenReturn(Optional.of(room));
            when(cache.getLastReadSeq(ROOM_ID, MEMBER_ID))
                    .thenReturn(Optional.of(10L));

            // when
            MyChatRoomSummary result = sut.getMyRoom(query);

            // then
            assertThat(result).isNotNull();

            verify(queryRepairService, never()).repairRoom(anyString());
            verify(persistence, never()).getLastReadSeq(anyString(), anyString());
            verify(cache, never()).updateLastReadSeq(anyString(), anyString(), anyLong());
            verify(cache, never()).updateActivityScore(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("채팅방 캐시는 히트하고 lastRead 캐시가 미스하면 영속 저장소 조회 후 active 캐시를 갱신한다")
        void should_refresh_active_cache_when_room_cache_hits_but_last_read_cache_misses() {
            // given
            GetMyChatRoomQuery query = getMyChatRoomQuery();

            when(room.getId()).thenReturn(ROOM_ID);
            when(room.lastMsgCreatedAtMs()).thenReturn(1_000L);
            when(room.hasUnread(10L)).thenReturn(true);

            when(cache.findById(ROOM_ID))
                    .thenReturn(Optional.of(room));
            when(cache.getLastReadSeq(ROOM_ID, MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(persistence.getLastReadSeq(ROOM_ID, MEMBER_ID))
                    .thenReturn(10L);

            // when
            MyChatRoomSummary result = sut.getMyRoom(query);

            // then
            assertThat(result).isNotNull();

            verify(persistence, times(1)).getLastReadSeq(ROOM_ID, MEMBER_ID);
            verify(cache, times(1)).updateLastReadSeq(ROOM_ID, MEMBER_ID, 10L);
            verify(cache, times(1)).updateActivityScore(
                    eq(ROOM_ID),
                    eq(MEMBER_ID),
                    eq(MyChatRoomScoreCalculator.unread(1_000L))
            );
        }

        @Test
        @DisplayName("채팅방 캐시 미스 시 repairRoom으로 복구하고 영속 저장소의 lastRead 기준으로 active 캐시를 갱신한다")
        void should_repair_room_and_use_persisted_last_read_when_room_cache_misses() {
            // given
            GetMyChatRoomQuery query = getMyChatRoomQuery();

            when(room.getId()).thenReturn(ROOM_ID);
            when(room.lastMsgCreatedAtMs()).thenReturn(1_000L);
            when(room.hasUnread(20L)).thenReturn(false);

            when(cache.findById(ROOM_ID))
                    .thenReturn(Optional.empty());
            when(queryRepairService.repairRoom(ROOM_ID))
                    .thenReturn(room);
            when(persistence.getLastReadSeq(ROOM_ID, MEMBER_ID))
                    .thenReturn(20L);

            // when
            MyChatRoomSummary result = sut.getMyRoom(query);

            // then
            assertThat(result).isNotNull();

            verify(queryRepairService, times(1)).repairRoom(ROOM_ID);
            verify(persistence, times(1)).getLastReadSeq(ROOM_ID, MEMBER_ID);
            verify(cache, times(1)).updateLastReadSeq(ROOM_ID, MEMBER_ID, 20L);
            verify(cache, times(1)).updateActivityScore(
                    eq(ROOM_ID),
                    eq(MEMBER_ID),
                    eq(MyChatRoomScoreCalculator.read(1_000L))
            );
        }

        @Test
        @DisplayName("active 캐시 갱신 실패 시에도 조회 Summary를 반환한다")
        void should_return_summary_even_when_active_cache_refresh_fails() {
            // given
            GetMyChatRoomQuery query = getMyChatRoomQuery();

            when(room.getId()).thenReturn(ROOM_ID);

            when(cache.findById(ROOM_ID))
                    .thenReturn(Optional.of(room));
            when(cache.getLastReadSeq(ROOM_ID, MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(persistence.getLastReadSeq(ROOM_ID, MEMBER_ID))
                    .thenReturn(10L);

            doThrow(new RuntimeException("redis failed"))
                    .when(cache)
                    .updateLastReadSeq(ROOM_ID, MEMBER_ID, 10L);

            // when
            MyChatRoomSummary result = sut.getMyRoom(query);

            // then
            assertThat(result).isNotNull();

            verify(cache, times(1)).updateLastReadSeq(ROOM_ID, MEMBER_ID, 10L);
            verify(cache, never()).updateActivityScore(anyString(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("listPopularRooms")
    class ListPopularRoomsTest {

        @Test
        @DisplayName("첫 페이지 전체 히트 시 인기 채팅방 목록을 캐시에서 반환한다")
        void should_return_cached_popular_rooms_when_first_page_all_hit() {
            // given
            ListPopularChatRoomsQuery query = firstPopularRoomsQuery();

            ChatRoomCacheLookupResult cached = allHit(List.of(ROOM_ID), List.of(room));

            when(cache.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(cached);

            // when
            List<ChatRoom> result = sut.listPopularRooms(query);

            // then
            assertThat(result).containsExactly(room);

            verify(queryRepairService, never()).repairPopularRooms(any(), anyInt());
            verify(queryRepairService, never()).repairPopularRoomsAfter(any());
            verify(queryRepairService, never()).repairRoomsByIds(anyList());
        }

        @Test
        @DisplayName("첫 페이지 인덱스가 없으면 repairPopularRooms로 전체 복구 조회한다")
        void should_repair_popular_rooms_when_first_page_index_missing() {
            // given
            ListPopularChatRoomsQuery query = firstPopularRoomsQuery();
            List<ChatRoom> repaired = List.of(room, room2);

            when(cache.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(noIndex());
            when(queryRepairService.repairPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(repaired);

            // when
            List<ChatRoom> result = sut.listPopularRooms(query);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(queryRepairService, times(1)).repairPopularRooms(ChatRoomCategory.FREE, 10);
            verify(queryRepairService, never()).repairPopularRoomsAfter(any());
            verify(queryRepairService, never()).repairRoomsByIds(anyList());
        }

        @Test
        @DisplayName("첫 페이지 일부 미스 시 미스난 roomId만 repairRoomsByIds로 복구하고 원래 순서대로 병합한다")
        void should_repair_only_missed_rooms_when_first_page_partially_misses() {
            // given
            ListPopularChatRoomsQuery query = firstPopularRoomsQuery();

            when(room.getId()).thenReturn(ROOM_ID);
            when(room2.getId()).thenReturn(ROOM_ID_2);

            ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                    List.of(ROOM_ID, ROOM_ID_2),
                    List.of(room),
                    List.of(ROOM_ID_2)
            );

            when(cache.listPopularRooms(ChatRoomCategory.FREE, 10))
                    .thenReturn(cached);
            when(queryRepairService.repairRoomsByIds(List.of(ROOM_ID_2)))
                    .thenReturn(List.of(room2));

            // when
            List<ChatRoom> result = sut.listPopularRooms(query);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(queryRepairService, never()).repairPopularRooms(any(), anyInt());
            verify(queryRepairService, never()).repairPopularRoomsAfter(any());
            verify(queryRepairService, times(1)).repairRoomsByIds(List.of(ROOM_ID_2));
        }

        @Test
        @DisplayName("커서 페이지 전체 히트 시 다음 인기 채팅방 목록을 캐시에서 반환한다")
        void should_return_cached_popular_rooms_after_when_cursor_page_all_hit() {
            // given
            ListPopularChatRoomsQuery query = popularRoomsAfterQuery();

            ChatRoomCacheLookupResult cached = allHit(List.of(ROOM_ID), List.of(room));

            when(cache.listPopularRoomsAfter(ChatRoomCategory.FREE, "last-room", 100L, 10))
                    .thenReturn(cached);

            // when
            List<ChatRoom> result = sut.listPopularRooms(query);

            // then
            assertThat(result).containsExactly(room);

            verify(queryRepairService, never()).repairPopularRooms(any(), anyInt());
            verify(queryRepairService, never()).repairPopularRoomsAfter(any());
            verify(queryRepairService, never()).repairRoomsByIds(anyList());
        }

        @Test
        @DisplayName("커서 페이지 인덱스가 없으면 repairPopularRoomsAfter로 다음 인기 채팅방 목록을 복구 조회한다")
        void should_repair_popular_rooms_after_when_cursor_page_index_missing() {
            // given
            ListPopularChatRoomsQuery query = popularRoomsAfterQuery();
            List<ChatRoom> repaired = List.of(room, room2);

            when(cache.listPopularRoomsAfter(ChatRoomCategory.FREE, "last-room", 100L, 10))
                    .thenReturn(noIndex());
            when(queryRepairService.repairPopularRoomsAfter(query))
                    .thenReturn(repaired);

            // when
            List<ChatRoom> result = sut.listPopularRooms(query);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(queryRepairService, never()).repairPopularRooms(any(), anyInt());
            verify(queryRepairService, times(1)).repairPopularRoomsAfter(query);
            verify(queryRepairService, never()).repairRoomsByIds(anyList());
        }

        @Test
        @DisplayName("커서 페이지 일부 미스 시 미스난 roomId만 repairRoomsByIds로 복구하고 원래 순서대로 병합한다")
        void should_repair_only_missed_rooms_when_cursor_page_partially_misses() {
            // given
            ListPopularChatRoomsQuery query = popularRoomsAfterQuery();

            when(room.getId()).thenReturn(ROOM_ID);
            when(room2.getId()).thenReturn(ROOM_ID_2);

            ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                    List.of(ROOM_ID, ROOM_ID_2),
                    List.of(room),
                    List.of(ROOM_ID_2)
            );

            when(cache.listPopularRoomsAfter(ChatRoomCategory.FREE, "last-room", 100L, 10))
                    .thenReturn(cached);
            when(queryRepairService.repairRoomsByIds(List.of(ROOM_ID_2)))
                    .thenReturn(List.of(room2));

            // when
            List<ChatRoom> result = sut.listPopularRooms(query);

            // then
            assertThat(result).containsExactly(room, room2);

            verify(queryRepairService, never()).repairPopularRooms(any(), anyInt());
            verify(queryRepairService, never()).repairPopularRoomsAfter(any());
            verify(queryRepairService, times(1)).repairRoomsByIds(List.of(ROOM_ID_2));
        }
    }

    @Nested
    @DisplayName("listMyRooms")
    class ListMyRoomsTest {

        @Test
        @DisplayName("첫 페이지 전체 히트 시 lastRead 캐시를 사용해 내 채팅방 Summary 목록을 반환한다")
        void should_return_my_rooms_from_cache_and_use_last_read_cache_when_first_page_all_hit() {
            // given
            ListMyChatRoomsQuery query = firstMyRoomsQuery();

            when(room.getId()).thenReturn("room-1");
            when(room2.getId()).thenReturn("room-2");

            ChatRoomCacheLookupResult cached = allHit(
                    List.of("room-1", "room-2"),
                    List.of(room, room2)
            );

            when(cache.listLatestActiveRooms(MEMBER_ID, 10))
                    .thenReturn(cached);
            when(cache.getLastReadSeq("room-1", MEMBER_ID))
                    .thenReturn(Optional.of(1L));
            when(cache.getLastReadSeq("room-2", MEMBER_ID))
                    .thenReturn(Optional.of(2L));

            // when
            List<MyChatRoomSummary> result = sut.listMyRooms(query);

            // then
            assertThat(result).hasSize(2);

            verify(queryRepairService, never()).repairMyRooms(anyString(), anyInt());
            verify(queryRepairService, never()).repairMyRoomsBefore(any(), anyLong());
            verify(queryRepairService, never()).repairRoomsByIds(anyList());
            verify(persistence, never()).getLastReadSeq(anyString(), anyString());
            verify(cache, never()).updateLastReadSeq(anyString(), anyString(), anyLong());
            verify(cache, never()).updateActivityScore(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("첫 페이지 일부 미스 시 미스난 roomId만 repairRoomsByIds로 복구하고 hit/repaired Summary를 원래 순서대로 병합한다")
        void should_repair_only_missed_rooms_when_first_page_partially_misses() {
            // given
            ListMyChatRoomsQuery query = firstMyRoomsQuery();

            when(room.getId()).thenReturn("room-1");
            when(room2.getId()).thenReturn("room-2");
            when(room2.lastMsgCreatedAtMs()).thenReturn(2_000L);
            when(room2.hasUnread(2L)).thenReturn(false);

            ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                    List.of("room-1", "room-2"),
                    List.of(room),
                    List.of("room-2")
            );

            when(cache.listLatestActiveRooms(MEMBER_ID, 10))
                    .thenReturn(cached);
            when(cache.getLastReadSeq("room-1", MEMBER_ID))
                    .thenReturn(Optional.of(1L));
            when(queryRepairService.repairRoomsByIds(List.of("room-2")))
                    .thenReturn(List.of(room2));
            when(persistence.getLastReadSeq("room-2", MEMBER_ID))
                    .thenReturn(2L);

            // when
            List<MyChatRoomSummary> result = sut.listMyRooms(query);

            // then
            assertThat(result).hasSize(2);

            verify(queryRepairService, never()).repairMyRooms(anyString(), anyInt());
            verify(queryRepairService, never()).repairMyRoomsBefore(any(), anyLong());
            verify(queryRepairService, times(1)).repairRoomsByIds(List.of("room-2"));
            verify(cache, never()).updateLastReadSeq("room-1", MEMBER_ID, 1L);
            verify(cache).updateLastReadSeq("room-2", MEMBER_ID, 2L);
            verify(cache).updateActivityScore(
                    "room-2",
                    MEMBER_ID,
                    MyChatRoomScoreCalculator.read(2_000L)
            );
        }

        @Test
        @DisplayName("첫 페이지 인덱스가 없으면 복구 조회 후 영속 저장소의 lastRead 기준으로 active 캐시를 갱신한다")
        void should_repair_my_rooms_and_use_persisted_last_read_when_first_page_index_missing() {
            // given
            ListMyChatRoomsQuery query = firstMyRoomsQuery();

            when(room.getId()).thenReturn("room-1");
            when(room.lastMsgCreatedAtMs()).thenReturn(1_000L);
            when(room.hasUnread(1L)).thenReturn(true);

            when(room2.getId()).thenReturn("room-2");
            when(room2.lastMsgCreatedAtMs()).thenReturn(2_000L);
            when(room2.hasUnread(2L)).thenReturn(false);

            List<ChatRoom> repaired = List.of(room, room2);

            when(cache.listLatestActiveRooms(MEMBER_ID, 10))
                    .thenReturn(noIndex());
            when(queryRepairService.repairMyRooms(MEMBER_ID, 10))
                    .thenReturn(repaired);
            when(persistence.getLastReadSeq("room-1", MEMBER_ID))
                    .thenReturn(1L);
            when(persistence.getLastReadSeq("room-2", MEMBER_ID))
                    .thenReturn(2L);

            // when
            List<MyChatRoomSummary> result = sut.listMyRooms(query);

            // then
            assertThat(result).hasSize(2);

            verify(queryRepairService, times(1)).repairMyRooms(MEMBER_ID, 10);
            verify(queryRepairService, never()).repairMyRoomsBefore(any(), anyLong());
            verify(queryRepairService, never()).repairRoomsByIds(anyList());

            verify(cache).updateLastReadSeq("room-1", MEMBER_ID, 1L);
            verify(cache).updateActivityScore(
                    "room-1",
                    MEMBER_ID,
                    MyChatRoomScoreCalculator.unread(1_000L)
            );

            verify(cache).updateLastReadSeq("room-2", MEMBER_ID, 2L);
            verify(cache).updateActivityScore(
                    "room-2",
                    MEMBER_ID,
                    MyChatRoomScoreCalculator.read(2_000L)
            );
        }

        @Test
        @DisplayName("커서 페이지 전체 히트 시 lastRead 캐시를 사용해 내 채팅방 Summary 목록을 반환한다")
        void should_return_my_rooms_before_from_cache_when_cursor_page_all_hit() {
            // given
            ListMyChatRoomsQuery query = myRoomsBeforeQuery(true, 1_000L);
            Long score = MyChatRoomScoreCalculator.unread(1_000L);

            when(room.getId()).thenReturn("room-1");

            ChatRoomCacheLookupResult cached = allHit(
                    List.of("room-1"),
                    List.of(room)
            );

            when(cache.listActiveRoomsBefore(MEMBER_ID, "last-room", score, 10))
                    .thenReturn(cached);
            when(cache.getLastReadSeq("room-1", MEMBER_ID))
                    .thenReturn(Optional.of(10L));

            // when
            List<MyChatRoomSummary> result = sut.listMyRooms(query);

            // then
            assertThat(result).hasSize(1);

            verify(queryRepairService, never()).repairMyRooms(anyString(), anyInt());
            verify(queryRepairService, never()).repairMyRoomsBefore(any(), anyLong());
            verify(queryRepairService, never()).repairRoomsByIds(anyList());
        }

        @Test
        @DisplayName("커서 페이지 전체 히트 시 lastUnreadFlag가 false면 read score 커서로 조회한다")
        void should_use_read_cursor_score_when_last_unread_flag_is_false() {
            // given
            ListMyChatRoomsQuery query = myRoomsBeforeQuery(false, 1_000L);
            Long score = MyChatRoomScoreCalculator.read(1_000L);

            when(room.getId()).thenReturn("room-1");

            ChatRoomCacheLookupResult cached = allHit(
                    List.of("room-1"),
                    List.of(room)
            );

            when(cache.listActiveRoomsBefore(MEMBER_ID, "last-room", score, 10))
                    .thenReturn(cached);
            when(cache.getLastReadSeq("room-1", MEMBER_ID))
                    .thenReturn(Optional.of(10L));

            // when
            List<MyChatRoomSummary> result = sut.listMyRooms(query);

            // then
            assertThat(result).hasSize(1);

            verify(cache, times(1)).listActiveRoomsBefore(MEMBER_ID, "last-room", score, 10);
        }

        @Test
        @DisplayName("커서 페이지에서 lastUnreadFlag가 null이면 read score 커서로 조회한다")
        void should_use_read_cursor_score_when_last_unread_flag_is_null() {
            // given
            ListMyChatRoomsQuery query = myRoomsBeforeQuery(null, 1_000L);
            Long score = MyChatRoomScoreCalculator.read(1_000L);

            when(room.getId()).thenReturn("room-1");

            ChatRoomCacheLookupResult cached = allHit(
                    List.of("room-1"),
                    List.of(room)
            );

            when(cache.listActiveRoomsBefore(MEMBER_ID, "last-room", score, 10))
                    .thenReturn(cached);
            when(cache.getLastReadSeq("room-1", MEMBER_ID))
                    .thenReturn(Optional.of(10L));

            // when
            List<MyChatRoomSummary> result = sut.listMyRooms(query);

            // then
            assertThat(result).hasSize(1);

            verify(cache, times(1)).listActiveRoomsBefore(MEMBER_ID, "last-room", score, 10);
        }

        @Test
        @DisplayName("커서 페이지에서 lastMsgCreatedAt이 null이면 0을 기준으로 커서 score를 계산한다")
        void should_use_zero_cursor_score_when_last_msg_created_at_is_null() {
            // given
            ListMyChatRoomsQuery query = myRoomsBeforeQuery(true, null);
            Long score = MyChatRoomScoreCalculator.unread(0L);

            when(room.getId()).thenReturn("room-1");

            ChatRoomCacheLookupResult cached = allHit(
                    List.of("room-1"),
                    List.of(room)
            );

            when(cache.listActiveRoomsBefore(MEMBER_ID, "last-room", score, 10))
                    .thenReturn(cached);
            when(cache.getLastReadSeq("room-1", MEMBER_ID))
                    .thenReturn(Optional.of(10L));

            // when
            List<MyChatRoomSummary> result = sut.listMyRooms(query);

            // then
            assertThat(result).hasSize(1);

            verify(cache, times(1)).listActiveRoomsBefore(MEMBER_ID, "last-room", score, 10);
        }

        @Test
        @DisplayName("커서 페이지 인덱스가 없으면 repairMyRoomsBefore로 복구 조회하고 lastRead 기준으로 active 캐시를 갱신한다")
        void should_repair_my_rooms_before_when_cursor_page_index_missing() {
            // given
            ListMyChatRoomsQuery query = myRoomsBeforeQuery(true, 1_000L);
            Long score = MyChatRoomScoreCalculator.unread(1_000L);

            when(room.getId()).thenReturn("room-1");
            when(room.lastMsgCreatedAtMs()).thenReturn(1_000L);
            when(room.hasUnread(10L)).thenReturn(false);

            when(cache.listActiveRoomsBefore(MEMBER_ID, "last-room", score, 10))
                    .thenReturn(noIndex());
            when(queryRepairService.repairMyRoomsBefore(query, score))
                    .thenReturn(List.of(room));
            when(persistence.getLastReadSeq("room-1", MEMBER_ID))
                    .thenReturn(10L);

            // when
            List<MyChatRoomSummary> result = sut.listMyRooms(query);

            // then
            assertThat(result).hasSize(1);

            verify(queryRepairService, never()).repairMyRooms(anyString(), anyInt());
            verify(queryRepairService, times(1)).repairMyRoomsBefore(query, score);
            verify(queryRepairService, never()).repairRoomsByIds(anyList());
            verify(cache, times(1)).updateLastReadSeq("room-1", MEMBER_ID, 10L);
            verify(cache, times(1)).updateActivityScore(
                    "room-1",
                    MEMBER_ID,
                    MyChatRoomScoreCalculator.read(1_000L)
            );
        }

        @Test
        @DisplayName("커서 페이지 일부 미스 시 미스난 roomId만 repairRoomsByIds로 복구하고 원래 순서대로 병합한다")
        void should_repair_only_missed_rooms_when_cursor_page_partially_misses() {
            // given
            ListMyChatRoomsQuery query = myRoomsBeforeQuery(true, 1_000L);
            Long score = MyChatRoomScoreCalculator.unread(1_000L);

            when(room.getId()).thenReturn("room-1");
            when(room2.getId()).thenReturn("room-2");
            when(room2.lastMsgCreatedAtMs()).thenReturn(2_000L);
            when(room2.hasUnread(2L)).thenReturn(false);

            ChatRoomCacheLookupResult cached = new ChatRoomCacheLookupResult(
                    List.of("room-1", "room-2"),
                    List.of(room),
                    List.of("room-2")
            );

            when(cache.listActiveRoomsBefore(MEMBER_ID, "last-room", score, 10))
                    .thenReturn(cached);
            when(cache.getLastReadSeq("room-1", MEMBER_ID))
                    .thenReturn(Optional.of(10L));
            when(queryRepairService.repairRoomsByIds(List.of("room-2")))
                    .thenReturn(List.of(room2));
            when(persistence.getLastReadSeq("room-2", MEMBER_ID))
                    .thenReturn(2L);

            // when
            List<MyChatRoomSummary> result = sut.listMyRooms(query);

            // then
            assertThat(result).hasSize(2);

            verify(queryRepairService, never()).repairMyRooms(anyString(), anyInt());
            verify(queryRepairService, never()).repairMyRoomsBefore(any(), anyLong());
            verify(queryRepairService, times(1)).repairRoomsByIds(List.of("room-2"));
            verify(cache).updateLastReadSeq("room-2", MEMBER_ID, 2L);
            verify(cache).updateActivityScore(
                    "room-2",
                    MEMBER_ID,
                    MyChatRoomScoreCalculator.read(2_000L)
            );
        }
    }

    @Nested
    @DisplayName("existsTitle")
    class ExistsTitleTest {

        @Test
        @DisplayName("캐시 히트 시 캐시 결과를 반환한다")
        void should_return_exists_title_from_cache_when_cache_hits() {
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
        @DisplayName("캐시 미스 시 영속 저장소에서 제목 존재 여부를 조회한다")
        void should_query_persistence_when_exists_title_cache_misses() {
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
    }

    private GetMyChatRoomQuery getMyChatRoomQuery() {
        return new GetMyChatRoomQuery(ROOM_ID, MEMBER_ID);
    }

    private ListPopularChatRoomsQuery firstPopularRoomsQuery() {
        return ListPopularChatRoomsQuery.firstPage(ChatRoomCategory.FREE, 10);
    }

    private ListPopularChatRoomsQuery popularRoomsAfterQuery() {
        return ListPopularChatRoomsQuery.nextPage(
                ChatRoomCategory.FREE,
                "last-room",
                100L,
                10
        );
    }

    private ListMyChatRoomsQuery firstMyRoomsQuery() {
        return ListMyChatRoomsQuery.firstPage(MEMBER_ID, 10);
    }

    private ListMyChatRoomsQuery myRoomsBeforeQuery(Boolean lastUnreadFlag, Long lastMsgCreatedAt) {
        return ListMyChatRoomsQuery.nextPage(
                MEMBER_ID,
                "last-room",
                lastUnreadFlag,
                lastMsgCreatedAt,
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
        return new ChatRoomCacheLookupResult(orderedIds, hits, List.of());
    }
}