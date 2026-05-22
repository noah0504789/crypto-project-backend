package chatroom.service;

import org.example.chatroom.domain.event.payload.ChatRoomPayload;
import org.example.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chatroom.application.service.ChatRoomEventService;
import org.example.chatroom.domain.event.*;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomEventServiceTest {

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomCachePort cache;

    @InjectMocks
    private ChatRoomEventService service;

    private static final String TX_ID = "tx-test";
    private static final String ROOM_ID = "room-1";
    private static final String HOST_ID = "host-1";
    private static final String MEMBER_ID = "member-1";

    @Nested
    @DisplayName("DB 이벤트 처리")
    class PersistenceEventTest {

        @Test
        @DisplayName("채팅방 생성 이벤트를 처리하면 payload를 ChatRoom으로 변환해 저장한다")
        void handlePersistedEvent() {
            // given
            ChatRoomPersistedEvent event = mock(ChatRoomPersistedEvent.class);

            ChatRoomPayload payload = ChatRoomPayload.builder()
                    .id(ROOM_ID)
                    .hostId(HOST_ID)
                    .title("테스트 채팅방")
                    .description("테스트 설명")
                    .category(ChatRoomCategory.FREE)
                    .memberIds(Set.of(HOST_ID, MEMBER_ID))
                    .createdAt(Instant.now())
                    .build();

            when(event.getPayload()).thenReturn(payload);

            // when
            service.handle(event, TX_ID);

            // then
            ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
            verify(persistence).save(captor.capture());

            ChatRoom saved = captor.getValue();

            assertThat(saved.getId()).isEqualTo(ROOM_ID);
            assertThat(saved.getHostId()).isEqualTo(HOST_ID);
            assertThat(saved.getTitle()).isEqualTo("테스트 채팅방");
            assertThat(saved.getDescription()).isEqualTo("테스트 설명");
            assertThat(saved.getCategory()).isEqualTo(ChatRoomCategory.FREE);
            assertThat(saved.getMemberIds()).containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);

            verifyNoInteractions(cache);
        }

        @Test
        @DisplayName("채팅방 수정 이벤트를 처리하면 persistence.updateAndReturn을 호출한다")
        void handleUpdatedEvent() {
            // given
            ChatRoomUpdatedEvent event = mock(ChatRoomUpdatedEvent.class);

            Map<String, Object> updated = Map.of(
                    "title", "수정된 제목",
                    "description", "수정된 설명"
            );

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getUpdated()).thenReturn(updated);

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).updateAndReturn(ROOM_ID, updated);
            verifyNoInteractions(cache);
        }

        @Test
        @DisplayName("채팅방 참여 이벤트를 처리하면 persistence.join을 호출한다")
        void handleJoinedEvent() {
            // given
            ChatRoomJoinedEvent event = mock(ChatRoomJoinedEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).join(ROOM_ID, MEMBER_ID);
            verifyNoInteractions(cache);
        }

        @Test
        @DisplayName("채팅방 퇴장 이벤트를 처리하면 persistence.leave를 호출한다")
        void handleLeavedEvent() {
            // given
            ChatRoomLeavedEvent event = mock(ChatRoomLeavedEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).leave(ROOM_ID, MEMBER_ID);
            verifyNoInteractions(cache);
        }

        @Test
        @DisplayName("채팅방 삭제 이벤트를 처리하면 persistence.deleteById를 호출한다")
        void handleDeletedEvent() {
            // given
            ChatRoomDeletedEvent event = mock(ChatRoomDeletedEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).deleteById(ROOM_ID);
            verifyNoInteractions(cache);
        }

        @Test
        @DisplayName("채팅방 활성 이벤트를 처리하면 persistence.active를 호출한다")
        void handleActiveEvent() {
            // given
            ChatRoomActiveEvent event = mock(ChatRoomActiveEvent.class);

            long lastMsgSeq = 10L;
            long lastMsgMs = 1_717_000_000_000L;

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);
            when(event.getLastMsgSeq()).thenReturn(lastMsgSeq);
            when(event.getLastMsgMs()).thenReturn(lastMsgMs);

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).active(ROOM_ID, MEMBER_ID, lastMsgSeq, lastMsgMs);
            verifyNoInteractions(cache);
        }
    }

    @Nested
    @DisplayName("캐시 이벤트 처리")
    class CacheEventTest {

        @Test
        @DisplayName("캐시 저장 이벤트 처리 시 채팅방이 존재하면 cache.warmUp을 호출한다")
        void handleCacheSaveEvent_roomExists() {
            // given
            ChatRoomCacheSaveEvent event = mock(ChatRoomCacheSaveEvent.class);
            ChatRoom chatRoom = mock(ChatRoom.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(persistence.findByIdWithLatest(ROOM_ID)).thenReturn(Optional.of(chatRoom));

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).findByIdWithLatest(ROOM_ID);
            verify(cache).warmUp(chatRoom);
        }

        @Test
        @DisplayName("캐시 저장 이벤트 처리 시 채팅방이 없으면 cache.warmUp을 호출하지 않는다")
        void handleCacheSaveEvent_roomNotFound() {
            // given
            ChatRoomCacheSaveEvent event = mock(ChatRoomCacheSaveEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(persistence.findByIdWithLatest(ROOM_ID)).thenReturn(Optional.empty());

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).findByIdWithLatest(ROOM_ID);
            verify(cache, never()).warmUp(any());
        }

        @Test
        @DisplayName("캐시 수정 이벤트 처리 시 채팅방이 존재하면 cache.recoverUpdate를 호출한다")
        void handleCacheUpdateEvent_roomExists() {
            // given
            ChatRoomCacheUpdateEvent event = mock(ChatRoomCacheUpdateEvent.class);
            ChatRoom chatRoom = mock(ChatRoom.class);

            String oldTitle = "이전 제목";

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getOldTitle()).thenReturn(oldTitle);
            when(persistence.findByIdWithLatest(ROOM_ID)).thenReturn(Optional.of(chatRoom));

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).findByIdWithLatest(ROOM_ID);
            verify(cache).recoverUpdate(chatRoom, oldTitle);
        }

        @Test
        @DisplayName("캐시 수정 이벤트 처리 시 채팅방이 없으면 cache.recoverUpdate를 호출하지 않는다")
        void handleCacheUpdateEvent_roomNotFound() {
            // given
            ChatRoomCacheUpdateEvent event = mock(ChatRoomCacheUpdateEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getOldTitle()).thenReturn("이전 제목");
            when(persistence.findByIdWithLatest(ROOM_ID)).thenReturn(Optional.empty());

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).findByIdWithLatest(ROOM_ID);
            verify(cache, never()).recoverUpdate(any(), anyString());
        }

        @Test
        @DisplayName("캐시 삭제 이벤트를 처리하면 cache.delete를 호출한다")
        void handleCacheDeleteEvent() {
            // given
            ChatRoomCacheDeleteEvent event = mock(ChatRoomCacheDeleteEvent.class);

            ChatRoomCategory category = ChatRoomCategory.FREE;
            String title = "테스트 채팅방";
            Set<String> memberIds = Set.of("member-1", "member-2");

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getCategory()).thenReturn(category);
            when(event.getTitle()).thenReturn(title);
            when(event.getMemberids()).thenReturn(memberIds);

            // when
            service.handle(event, TX_ID);

            // then
            verify(cache).delete(ROOM_ID, category, title, memberIds);
            verifyNoInteractions(persistence);
        }

        @Test
        @DisplayName("활동 캐시 무효화 이벤트를 처리하면 cache.invalidateActivity를 호출한다")
        void handleCacheActivityInvalidateEvent() {
            // given
            ChatRoomCacheActivityInvalidateEvent event = mock(ChatRoomCacheActivityInvalidateEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);

            // when
            service.handle(event, TX_ID);

            // then
            verify(cache).invalidateActivity(ROOM_ID, MEMBER_ID);
            verifyNoInteractions(persistence);
        }

        @Test
        @DisplayName("정보 캐시 무효화 이벤트를 처리하면 cache.invalidateInfo를 호출한다")
        void handleCacheInfoInvalidateEvent() {
            // given
            ChatRoomCacheInfoInvalidateEvent event = mock(ChatRoomCacheInfoInvalidateEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);

            // when
            service.handle(event, TX_ID);

            // then
            verify(cache).invalidateInfo(ROOM_ID);
            verifyNoInteractions(persistence);
        }
    }
}