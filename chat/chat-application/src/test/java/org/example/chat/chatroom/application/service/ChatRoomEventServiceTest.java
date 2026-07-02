package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.domain.event.*;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomDlqEventList;
import org.example.chat.chatroom.domain.event.payload.ChatRoomPayload;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.exception.TemporaryChatCacheException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.dlq.application.port.out.DlqEventListPublishPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomEventServiceTest {

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomCachePort cache;

    @Mock
    private DlqEventListPublishPort dlqEventListPublishPort;

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
            verifyNoInteractions(dlqEventListPublishPort);
        }

        @Test
        @DisplayName("채팅방 수정 이벤트를 처리하면 persistence.updateAndReturn을 호출한다")
        void handleUpdatedEvent() {
            // given
            ChatRoomUpdatedEvent event = mock(ChatRoomUpdatedEvent.class);

            ChatRoomUpdatedPayload updated = new ChatRoomUpdatedPayload(
                    "수정된 제목",
                    "수정된 설명",
                    null
            );

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getUpdated()).thenReturn(updated);

            // when
            service.handle(event, TX_ID);

            // then
            verify(persistence).updateAndReturn(ROOM_ID, updated.toUpdateMap());
            verifyNoInteractions(cache);
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
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
            verifyNoInteractions(dlqEventListPublishPort);
        }
    }

    @Nested
    @DisplayName("DB 이벤트 recover")
    class PersistenceRecoverTest {

        @Test
        @DisplayName("채팅방 생성 이벤트 재시도 소진 시 persist DLQ 이벤트를 발행한다")
        void recoverPersistedEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    new TemporaryChatPersistenceException("mongo failed", new RuntimeException("temporary"));

            ChatRoomPersistedEvent event = mock(ChatRoomPersistedEvent.class);
            ChatRoomPayload payload = chatRoomPayload();
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getPayload()).thenReturn(payload);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.fromPayload(payload)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverPersist(exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }

        @Test
        @DisplayName("채팅방 수정 이벤트 재시도 소진 시 update DLQ 이벤트를 발행한다")
        void recoverUpdatedEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    new TemporaryChatPersistenceException("mongo failed", new RuntimeException("temporary"));

            ChatRoomUpdatedEvent event = mock(ChatRoomUpdatedEvent.class);
            ChatRoomUpdatedPayload updated = new ChatRoomUpdatedPayload("title", "description", ChatRoomCategory.FREE);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getUpdated()).thenReturn(updated);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverUpdate(event, exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }

        @Test
        @DisplayName("채팅방 참여 이벤트 재시도 소진 시 join DLQ 이벤트를 발행한다")
        void recoverJoinedEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    new TemporaryChatPersistenceException("mongo failed", new RuntimeException("temporary"));

            ChatRoomJoinedEvent event = mock(ChatRoomJoinedEvent.class);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverJoin(event, exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }

        @Test
        @DisplayName("채팅방 퇴장 이벤트 재시도 소진 시 leave DLQ 이벤트를 발행한다")
        void recoverLeavedEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    new TemporaryChatPersistenceException("mongo failed", new RuntimeException("temporary"));

            ChatRoomLeavedEvent event = mock(ChatRoomLeavedEvent.class);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverLeave(event, exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }

        @Test
        @DisplayName("채팅방 삭제 이벤트 재시도 소진 시 delete DLQ 이벤트를 발행한다")
        void recoverDeletedEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    new TemporaryChatPersistenceException("mongo failed", new RuntimeException("temporary"));

            ChatRoomDeletedEvent event = mock(ChatRoomDeletedEvent.class);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getCategory()).thenReturn(ChatRoomCategory.FREE);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofIdAndCategory(ROOM_ID, ChatRoomCategory.FREE)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverDelete(exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }

        @Test
        @DisplayName("채팅방 활성 이벤트 재시도 소진 시 active DLQ 이벤트를 발행한다")
        void recoverActiveEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    new TemporaryChatPersistenceException("mongo failed", new RuntimeException("temporary"));

            ChatRoomActiveEvent event = mock(ChatRoomActiveEvent.class);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            long lastMsgSeq = 10L;
            long lastMsgMs = 1_717_000_000_000L;

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);
            when(event.getLastMsgSeq()).thenReturn(lastMsgSeq);
            when(event.getLastMsgMs()).thenReturn(lastMsgMs);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverActive(event, exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }
    }

    @Nested
    @DisplayName("캐시 이벤트 recover")
    class CacheRecoverTest {

        @Test
        @DisplayName("캐시 저장 이벤트 재시도 소진 시 cacheSave DLQ 이벤트를 발행한다")
        void recoverCacheSaveEvent() {
            // given
            TemporaryChatCacheException exception =
                    new TemporaryChatCacheException("redis failed", new RuntimeException("temporary"));

            ChatRoomCacheSaveEvent event = mock(ChatRoomCacheSaveEvent.class);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverCacheSave(exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }

        @Test
        @DisplayName("캐시 수정 이벤트 재시도 소진 시 cacheUpdate DLQ 이벤트를 발행한다")
        void recoverCacheUpdateEvent() {
            // given
            TemporaryChatCacheException exception =
                    new TemporaryChatCacheException("redis failed", new RuntimeException("temporary"));

            ChatRoomCacheUpdateEvent event = mock(ChatRoomCacheUpdateEvent.class);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverCacheUpdate(event, exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }

        @Test
        @DisplayName("캐시 삭제 이벤트 재시도 소진 시 cacheDelete DLQ 이벤트를 발행한다")
        void recoverCacheDeleteEvent() {
            // given
            TemporaryChatCacheException exception =
                    new TemporaryChatCacheException("redis failed", new RuntimeException("temporary"));

            ChatRoomCacheDeleteEvent event = mock(ChatRoomCacheDeleteEvent.class);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverCacheDelete(event, exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }

        @Test
        @DisplayName("활동 캐시 무효화 이벤트 재시도 소진 시 cacheActivityInvalidate DLQ 이벤트를 발행한다")
        void recoverCacheActivityInvalidateEvent() {
            // given
            TemporaryChatCacheException exception =
                    new TemporaryChatCacheException("redis failed", new RuntimeException("temporary"));

            ChatRoomCacheActivityInvalidateEvent event = mock(ChatRoomCacheActivityInvalidateEvent.class);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getMemberId()).thenReturn(MEMBER_ID);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverCacheInvalidateActivity(event, exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }

        @Test
        @DisplayName("정보 캐시 무효화 이벤트 재시도 소진 시 cacheInfoInvalidate DLQ 이벤트를 발행한다")
        void recoverCacheInfoInvalidateEvent() {
            // given
            TemporaryChatCacheException exception =
                    new TemporaryChatCacheException("redis failed", new RuntimeException("temporary"));

            ChatRoomCacheInfoInvalidateEvent event = mock(ChatRoomCacheInfoInvalidateEvent.class);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverCacheInvalidateInfo(event, exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }
    }

    @Nested
    @DisplayName("recover fallback")
    class RecoverFallbackTest {

        @Test
        @DisplayName("DLQ 발행이 실패해도 recover는 예외를 전파하지 않는다")
        void recover_should_not_throw_when_dlq_publish_fails() {
            // given
            TemporaryChatPersistenceException exception =
                    new TemporaryChatPersistenceException("mongo failed", new RuntimeException("temporary"));

            ChatRoomUpdatedEvent event = mock(ChatRoomUpdatedEvent.class);
            ChatRoomUpdatedPayload updated = new ChatRoomUpdatedPayload("title", "description", ChatRoomCategory.FREE);
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomDlqEventList dlqEventList = mock(ChatRoomDlqEventList.class);

            when(event.getId()).thenReturn(ROOM_ID);
            when(event.getUpdated()).thenReturn(updated);
            when(domain.pullDlqEventList()).thenReturn(dlqEventList);

            doThrow(new RuntimeException("dlq publish failed"))
                    .when(dlqEventListPublishPort)
                    .publish(dlqEventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(ROOM_ID)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> service.recover(exception, event, TX_ID));

                verify(domain).recoverUpdate(event, exception.getMessage());
                verify(domain).pullDlqEventList();
                verify(dlqEventListPublishPort).publish(dlqEventList);
            }
        }
    }

    private ChatRoomPayload chatRoomPayload() {
        return ChatRoomPayload.builder()
                .id(ROOM_ID)
                .hostId(HOST_ID)
                .title("테스트 채팅방")
                .description("테스트 설명")
                .category(ChatRoomCategory.FREE)
                .memberIds(Set.of(HOST_ID, MEMBER_ID))
                .createdAt(Instant.now())
                .build();
    }
}