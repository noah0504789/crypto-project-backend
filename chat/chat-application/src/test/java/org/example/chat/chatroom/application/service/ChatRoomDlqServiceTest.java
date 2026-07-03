package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.domain.event.dlq.*;
import org.example.chat.chatroom.domain.event.payload.ChatRoomPayload;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomDlqServiceTest {

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomCachePort cache;

    @InjectMocks
    private ChatRoomDlqService service;

    private static final String ROOM_ID = "room-1";
    private static final String HOST_ID = "host-1";
    private static final String MEMBER_ID = "member-1";

    @Nested
    @DisplayName("DLQ DB 이벤트 처리")
    class PersistenceDlqEventTest {

        @Test
        @DisplayName("채팅방 생성 DLQ 이벤트를 처리하면 payload를 ChatRoom으로 변환해 저장한다")
        void handlePersistedDlqEvent() {
            // given
            ChatRoomPayload payload = ChatRoomPayload.builder()
                    .id(ROOM_ID)
                    .hostId(HOST_ID)
                    .title("테스트 채팅방")
                    .description("테스트 설명")
                    .category(ChatRoomCategory.FREE)
                    .memberIds(Set.of(HOST_ID, MEMBER_ID))
                    .createdAt(Instant.now())
                    .build();

            ChatRoomPersistedDlqEvent event = mock(ChatRoomPersistedDlqEvent.class);
            when(event.getPayload()).thenReturn(payload);

            // when
            service.handle(event);

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
        @DisplayName("채팅방 수정 DLQ 이벤트를 처리하면 persistence.updateAndReturn을 호출한다")
        void handleUpdatedDlqEvent() {
            // given
            ChatRoomUpdatedPayload updated = new ChatRoomUpdatedPayload(
                    "수정된 제목",
                    "수정된 설명",
                    null
            );

            ChatRoomUpdatedDlqEvent event = mock(ChatRoomUpdatedDlqEvent.class);
            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getUpdated()).thenReturn(updated);

            // when
            service.handle(event);

            // then
            verify(persistence).updateRoomAndReturn(ROOM_ID, updated.toUpdateMap());
            verifyNoInteractions(cache);
        }

        @Test
        @DisplayName("채팅방 삭제 DLQ 이벤트를 처리하면 persistence.deleteById를 호출한다")
        void handleDeletedDlqEvent() {
            // given
            ChatRoomDeletedDlqEvent event = mock(ChatRoomDeletedDlqEvent.class);
            when(event.getId()).thenReturn(ROOM_ID);

            // when
            service.handle(event);

            // then
            verify(persistence).deleteById(ROOM_ID);
            verifyNoInteractions(cache);
        }

        @Test
        @DisplayName("채팅방 참여 DLQ 이벤트를 처리하면 persistence.join을 호출한다")
        void handleJoinedDlqEvent() {
            // given
            ChatRoomJoinedDlqEvent event = mock(ChatRoomJoinedDlqEvent.class);
            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);

            // when
            service.handle(event);

            // then
            verify(persistence).joinMembership(ROOM_ID, MEMBER_ID);
            verifyNoInteractions(cache);
        }

        @Test
        @DisplayName("채팅방 퇴장 DLQ 이벤트를 처리하면 persistence.leave를 호출한다")
        void handleLeavedDlqEvent() {
            // given
            ChatRoomLeavedDlqEvent event = mock(ChatRoomLeavedDlqEvent.class);
            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);

            // when
            service.handle(event);

            // then
            verify(persistence).leaveMembership(ROOM_ID, MEMBER_ID);
            verifyNoInteractions(cache);
        }

        @Test
        @DisplayName("채팅방 활성 DLQ 이벤트를 처리하면 persistence.active를 호출한다")
        void handleActiveDlqEvent() {
            // given
            long lastMsgSeq = 10L;
            long lastMsgMs = 1_717_000_000_000L;

            ChatRoomActiveDlqEvent event = mock(ChatRoomActiveDlqEvent.class);
            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);
            when(event.getLastMsgSeq()).thenReturn(lastMsgSeq);
            when(event.getLastMsgMs()).thenReturn(lastMsgMs);

            // when
            service.handle(event);

            // then
            verify(persistence).activateMembership(ROOM_ID, MEMBER_ID, lastMsgSeq, lastMsgMs);
            verifyNoInteractions(cache);
        }
    }

    @Nested
    @DisplayName("DLQ 캐시 이벤트 처리")
    class CacheDlqEventTest {

        @Test
        @DisplayName("캐시 저장 DLQ 이벤트 처리 시 채팅방이 존재하면 cache.warmUp을 호출한다")
        void handleCacheSaveDlqEvent_roomExists() {
            // given
            ChatRoomCacheSaveDlqEvent event = mock(ChatRoomCacheSaveDlqEvent.class);
            ChatRoom chatRoom = mock(ChatRoom.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(persistence.findByIdWithLatestMessage(ROOM_ID)).thenReturn(Optional.of(chatRoom));

            // when
            service.handle(event);

            // then
            verify(persistence).findByIdWithLatestMessage(ROOM_ID);
            verify(cache).warmUp(chatRoom);
        }

        @Test
        @DisplayName("캐시 저장 DLQ 이벤트 처리 시 채팅방이 없으면 cache.warmUp을 호출하지 않는다")
        void handleCacheSaveDlqEvent_roomNotFound() {
            // given
            ChatRoomCacheSaveDlqEvent event = mock(ChatRoomCacheSaveDlqEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(persistence.findByIdWithLatestMessage(ROOM_ID)).thenReturn(Optional.empty());

            // when
            service.handle(event);

            // then
            verify(persistence).findByIdWithLatestMessage(ROOM_ID);
            verify(cache, never()).warmUp(any());
        }

        @Test
        @DisplayName("캐시 수정 DLQ 이벤트 처리 시 채팅방이 존재하면 cache.recoverUpdate를 호출한다")
        void handleCacheUpdateDlqEvent_roomExists() {
            // given
            String oldTitle = "이전 제목";
            ChatRoom chatRoom = mock(ChatRoom.class);

            ChatRoomCacheUpdateDlqEvent event = mock(ChatRoomCacheUpdateDlqEvent.class);
            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getOldTitle()).thenReturn(oldTitle);
            when(persistence.findByIdWithLatestMessage(ROOM_ID)).thenReturn(Optional.of(chatRoom));

            // when
            service.handle(event);

            // then
            verify(persistence).findByIdWithLatestMessage(ROOM_ID);
            verify(cache).recoverRoomUpdate(chatRoom, oldTitle);
        }

        @Test
        @DisplayName("캐시 수정 DLQ 이벤트 처리 시 채팅방이 없으면 cache.recoverUpdate를 호출하지 않는다")
        void handleCacheUpdateDlqEvent_roomNotFound() {
            // given
            ChatRoomCacheUpdateDlqEvent event = mock(ChatRoomCacheUpdateDlqEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getOldTitle()).thenReturn("이전 제목");
            when(persistence.findByIdWithLatestMessage(ROOM_ID)).thenReturn(Optional.empty());

            // when
            service.handle(event);

            // then
            verify(persistence).findByIdWithLatestMessage(ROOM_ID);
            verify(cache, never()).recoverRoomUpdate(any(), anyString());
        }

        @Test
        @DisplayName("캐시 삭제 DLQ 이벤트를 처리하면 cache.delete를 호출한다")
        void handleCacheDeleteDlqEvent() {
            // given
            ChatRoomCategory category = ChatRoomCategory.FREE;
            String title = "테스트 채팅방";
            Set<String> memberIds = Set.of("member-1", "member-2");

            ChatRoomCacheDeleteDlqEvent event = mock(ChatRoomCacheDeleteDlqEvent.class);
            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getCategory()).thenReturn(category);
            when(event.getTitle()).thenReturn(title);
            when(event.getMemberIds()).thenReturn(memberIds);

            // when
            service.handle(event);

            // then
            verify(cache).deleteRoom(ROOM_ID, category, title, memberIds);
            verifyNoInteractions(persistence);
        }

        @Test
        @DisplayName("활동 캐시 무효화 DLQ 이벤트를 처리하면 cache.invalidateActivity를 호출한다")
        void handleCacheActivityInvalidateDlqEvent() {
            // given
            ChatRoomCacheActivityInvalidateDlqEvent event = mock(ChatRoomCacheActivityInvalidateDlqEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);

            // when
            service.handle(event);

            // then
            verify(cache).invalidateMembershipActivity(ROOM_ID, MEMBER_ID);
            verifyNoInteractions(persistence);
        }

        @Test
        @DisplayName("정보 캐시 무효화 DLQ 이벤트를 처리하면 cache.invalidateInfo를 호출한다")
        void handleCacheInfoInvalidateDlqEvent() {
            // given
            ChatRoomCacheInfoInvalidateDlqEvent event = mock(ChatRoomCacheInfoInvalidateDlqEvent.class);

            when(event.getId()).thenReturn(ROOM_ID);

            // when
            service.handle(event);

            // then
            verify(cache).invalidateRoomInfo(ROOM_ID);
            verifyNoInteractions(persistence);
        }
    }
}