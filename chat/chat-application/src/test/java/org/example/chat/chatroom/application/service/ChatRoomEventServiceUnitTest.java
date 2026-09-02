package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.application.event.*;
import org.example.chat.chatroom.application.event.dlq.ChatRoomDlqEventList;
import org.example.chat.chatroom.application.event.payload.ChatRoomPersistPayload;
import org.example.chat.chatroom.application.mapper.ChatRoomPayloadMapper;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.event.payload.ChatRoomUpdatedPayload;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomEventServiceUnitTest {

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomCachePort cache;

    @Mock
    private DlqEventListPublishPort dlqEventListPublishPort;

    @InjectMocks
    private ChatRoomEventService sut;

    private static final String TX_ID = "tx-test";
    private static final String ROOM_ID = "room-1";
    private static final String HOST_ID = "host-1";
    private static final String MEMBER_ID = "member-1";
    private static final String TITLE = "title";
    private static final String OLD_TITLE = "old-title";
    private static final String ERROR_MESSAGE = "temporary error";

    private final ChatRoomCategory category = ChatRoomCategory.FREE;

    @Nested
    @DisplayName("handle - persistence events")
    class HandlePersistenceEventsTest {

        @Test
        @DisplayName("ChatRoomPersistedEvent를 처리하면 payload를 도메인으로 변환한 뒤 저장한다")
        void handle_shouldSaveChatRoom_whenPersistedEvent() {
            // given
            ChatRoom domain = chatRoom();
            ChatRoomPersistPayload payload = ChatRoomPayloadMapper.fromDomain(domain);

            ChatRoomPersistedEvent event = new ChatRoomPersistedEvent(payload);

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .save(argThat(saved ->
                            saved.getId().equals(ROOM_ID)
                                    && saved.getHostId().equals(HOST_ID)
                                    && saved.getTitle().equals(TITLE)
                                    && saved.getDescription().equals("description")
                                    && saved.getCategory() == category
                                    && saved.getMemberIds().contains(HOST_ID)
                                    && saved.getMsgCnt().equals(0L)
                    ));

            then(cache)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomUpdatedEvent를 처리하면 updateMap으로 채팅방을 수정한다")
        void handle_shouldUpdateRoom_whenUpdatedEvent() {
            // given
            ChatRoomUpdatedPayload updatedPayload = mock(ChatRoomUpdatedPayload.class);
            Map<String, Object> updateMap = Map.of("title", "new-title");

            given(updatedPayload.toUpdateMap()).willReturn(updateMap);

            ChatRoomUpdatedEvent event = new ChatRoomUpdatedEvent(
                    ROOM_ID,
                    updatedPayload
            );

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .updateRoomAndReturn(ROOM_ID, updateMap);

            then(cache)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomJoinedEvent를 처리하면 채팅방 멤버십을 추가한다")
        void handle_shouldJoinMembership_whenJoinedEvent() {
            // given
            ChatRoomJoinedEvent event = new ChatRoomJoinedEvent(
                    ROOM_ID,
                    MEMBER_ID
            );

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .joinMembership(ROOM_ID, MEMBER_ID);

            then(cache)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomLeavedEvent를 처리하면 채팅방 멤버십을 제거한다")
        void handle_shouldLeaveMembership_whenLeavedEvent() {
            // given
            ChatRoomLeavedEvent event = new ChatRoomLeavedEvent(
                    ROOM_ID,
                    MEMBER_ID
            );

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .leaveMembership(ROOM_ID, MEMBER_ID);

            then(cache)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomDeletedEvent를 처리하면 채팅방을 삭제한다")
        void handle_shouldDeleteRoom_whenDeletedEvent() {
            // given
            ChatRoomDeletedEvent event = new ChatRoomDeletedEvent(
                    ROOM_ID,
                    category
            );

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .deleteById(ROOM_ID);

            then(cache)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomActiveEvent를 처리하면 멤버 활동 정보를 갱신한다")
        void handle_shouldActivateMembership_whenActiveEvent() {
            // given
            Long lastMsgSeq = 10L;
            Long lastMsgMs = 100L;

            ChatRoomActiveEvent event = new ChatRoomActiveEvent(
                    ROOM_ID,
                    MEMBER_ID,
                    lastMsgSeq,
                    lastMsgMs
            );

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .activateMembership(ROOM_ID, MEMBER_ID, lastMsgSeq);

            then(cache)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handle - cache events")
    class HandleCacheEventsTest {

        @Test
        @DisplayName("ChatRoomCacheSaveEvent를 처리하면 최신 메시지 포함 채팅방을 조회해서 캐시를 warmUp 한다")
        void handle_shouldWarmUpCache_whenCacheSaveEventAndRoomExists() {
            // given
            ChatRoom domain = chatRoomWithLatest();

            ChatRoomCacheSaveEvent event = new ChatRoomCacheSaveEvent(
                    ROOM_ID
            );

            given(persistence.findByIdWithLatestMessage(ROOM_ID))
                    .willReturn(Optional.of(domain));

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .findByIdWithLatestMessage(ROOM_ID);

            then(cache)
                    .should()
                    .warmUp(domain);

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheSaveEvent 처리 시 채팅방이 없으면 캐시 warmUp을 수행하지 않는다")
        void handle_shouldNotWarmUpCache_whenCacheSaveEventAndRoomDoesNotExist() {
            // given
            ChatRoomCacheSaveEvent event = new ChatRoomCacheSaveEvent(
                    ROOM_ID
            );

            given(persistence.findByIdWithLatestMessage(ROOM_ID))
                    .willReturn(Optional.empty());

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .findByIdWithLatestMessage(ROOM_ID);

            then(cache)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheUpdateEvent를 처리하면 최신 메시지 포함 채팅방을 조회해서 캐시 수정 복구를 수행한다")
        void handle_shouldRecoverRoomUpdate_whenCacheUpdateEventAndRoomExists() {
            // given
            ChatRoom domain = chatRoomWithLatest();

            ChatRoomCacheUpdateEvent event = new ChatRoomCacheUpdateEvent(
                    ROOM_ID,
                    OLD_TITLE
            );

            given(persistence.findByIdWithLatestMessage(ROOM_ID))
                    .willReturn(Optional.of(domain));

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .findByIdWithLatestMessage(ROOM_ID);

            then(cache)
                    .should()
                    .recoverRoomUpdate(domain, OLD_TITLE);

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheUpdateEvent 처리 시 채팅방이 없으면 캐시 수정 복구를 수행하지 않는다")
        void handle_shouldNotRecoverRoomUpdate_whenCacheUpdateEventAndRoomDoesNotExist() {
            // given
            ChatRoomCacheUpdateEvent event = new ChatRoomCacheUpdateEvent(
                    ROOM_ID,
                    OLD_TITLE
            );

            given(persistence.findByIdWithLatestMessage(ROOM_ID))
                    .willReturn(Optional.empty());

            // when
            sut.handle(event, TX_ID);

            // then
            then(persistence)
                    .should()
                    .findByIdWithLatestMessage(ROOM_ID);

            then(cache)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheDeleteEvent를 처리하면 캐시에서 채팅방을 삭제한다")
        void handle_shouldDeleteRoomFromCache_whenCacheDeleteEvent() {
            // given
            Set<String> memberIds = Set.of(HOST_ID, MEMBER_ID);

            ChatRoomCacheDeleteEvent event = new ChatRoomCacheDeleteEvent(
                    ROOM_ID,
                    category,
                    TITLE,
                    memberIds
            );

            // when
            sut.handle(event, TX_ID);

            // then
            then(cache)
                    .should()
                    .deleteRoom(
                            ROOM_ID,
                            category,
                            TITLE,
                            memberIds
                    );

            then(persistence)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheActivityInvalidateEvent를 처리하면 멤버 활동 캐시를 무효화한다")
        void handle_shouldInvalidateMembershipActivity_whenCacheActivityInvalidateEvent() {
            // given
            ChatRoomCacheActivityInvalidateEvent event =
                    new ChatRoomCacheActivityInvalidateEvent(
                            ROOM_ID,
                            MEMBER_ID
                    );

            // when
            sut.handle(event, TX_ID);

            // then
            then(cache)
                    .should()
                    .invalidateMembershipActivity(ROOM_ID, MEMBER_ID);

            then(persistence)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheInfoInvalidateEvent를 처리하면 채팅방 정보 캐시를 무효화한다")
        void handle_shouldInvalidateRoomInfo_whenCacheInfoInvalidateEvent() {
            // given
            ChatRoomCacheInfoInvalidateEvent event =
                    new ChatRoomCacheInfoInvalidateEvent(
                            ROOM_ID
                    );

            // when
            sut.handle(event, TX_ID);

            // then
            then(cache)
                    .should()
                    .invalidateRoomInfo(ROOM_ID);

            then(persistence)
                    .shouldHaveNoInteractions();

            then(dlqEventListPublishPort)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("recover - persistence events")
    class RecoverPersistenceEventsTest {

        @Test
        @DisplayName("ChatRoomPersistedEvent 재시도 소진 시 persisted DLQ 이벤트를 발행한다")
        void recover_shouldPublishPersistedDlqEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    temporaryPersistenceException();

            ChatRoom domain = chatRoom();
            ChatRoomPersistPayload payload = ChatRoomPayloadMapper.fromDomain(domain);
            ChatRoomPersistedEvent event = new ChatRoomPersistedEvent(payload);

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomUpdatedEvent 재시도 소진 시 updated DLQ 이벤트를 발행한다")
        void recover_shouldPublishUpdatedDlqEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    temporaryPersistenceException();

            ChatRoomUpdatedPayload updatedPayload = mock(ChatRoomUpdatedPayload.class);

            ChatRoomUpdatedEvent event = new ChatRoomUpdatedEvent(
                    ROOM_ID,
                    updatedPayload
            );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomJoinedEvent 재시도 소진 시 joined DLQ 이벤트를 발행한다")
        void recover_shouldPublishJoinedDlqEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    temporaryPersistenceException();

            ChatRoomJoinedEvent event = new ChatRoomJoinedEvent(
                    ROOM_ID,
                    MEMBER_ID
            );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomLeavedEvent 재시도 소진 시 leaved DLQ 이벤트를 발행한다")
        void recover_shouldPublishLeavedDlqEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    temporaryPersistenceException();

            ChatRoomLeavedEvent event = new ChatRoomLeavedEvent(
                    ROOM_ID,
                    MEMBER_ID
            );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomDeletedEvent 재시도 소진 시 deleted DLQ 이벤트를 발행한다")
        void recover_shouldPublishDeletedDlqEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    temporaryPersistenceException();

            ChatRoomDeletedEvent event = new ChatRoomDeletedEvent(
                    ROOM_ID,
                    category
            );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomActiveEvent 재시도 소진 시 active DLQ 이벤트를 발행한다")
        void recover_shouldPublishActiveDlqEvent() {
            // given
            TemporaryChatPersistenceException exception =
                    temporaryPersistenceException();

            ChatRoomActiveEvent event = new ChatRoomActiveEvent(
                    ROOM_ID,
                    MEMBER_ID,
                    10L,
                    100L
            );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("recover - cache events")
    class RecoverCacheEventsTest {

        @Test
        @DisplayName("ChatRoomCacheSaveEvent 재시도 소진 시 cache save DLQ 이벤트를 발행한다")
        void recover_shouldPublishCacheSaveDlqEvent() {
            // given
            TemporaryChatCacheException exception =
                    temporaryCacheException();

            ChatRoomCacheSaveEvent event = new ChatRoomCacheSaveEvent(
                    ROOM_ID
            );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheUpdateEvent 재시도 소진 시 cache update DLQ 이벤트를 발행한다")
        void recover_shouldPublishCacheUpdateDlqEvent() {
            // given
            TemporaryChatCacheException exception =
                    temporaryCacheException();

            ChatRoomCacheUpdateEvent event = new ChatRoomCacheUpdateEvent(
                    ROOM_ID,
                    OLD_TITLE
            );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheDeleteEvent 재시도 소진 시 cache delete DLQ 이벤트를 발행한다")
        void recover_shouldPublishCacheDeleteDlqEvent() {
            // given
            TemporaryChatCacheException exception =
                    temporaryCacheException();

            Set<String> memberIds = Set.of(HOST_ID, MEMBER_ID);

            ChatRoomCacheDeleteEvent event = new ChatRoomCacheDeleteEvent(
                    ROOM_ID,
                    category,
                    TITLE,
                    memberIds
            );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheActivityInvalidateEvent 재시도 소진 시 cache activity invalidate DLQ 이벤트를 발행한다")
        void recover_shouldPublishCacheActivityInvalidateDlqEvent() {
            // given
            TemporaryChatCacheException exception =
                    temporaryCacheException();

            ChatRoomCacheActivityInvalidateEvent event =
                    new ChatRoomCacheActivityInvalidateEvent(
                            ROOM_ID,
                            MEMBER_ID
                    );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ChatRoomCacheInfoInvalidateEvent 재시도 소진 시 cache info invalidate DLQ 이벤트를 발행한다")
        void recover_shouldPublishCacheInfoInvalidateDlqEvent() {
            // given
            TemporaryChatCacheException exception =
                    temporaryCacheException();

            ChatRoomCacheInfoInvalidateEvent event =
                    new ChatRoomCacheInfoInvalidateEvent(
                            ROOM_ID
                    );

            // when
            sut.recover(exception, event, TX_ID);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));

            then(persistence)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("DLQ 발행 자체가 실패해도 recover는 예외를 밖으로 던지지 않는다")
        void recover_shouldSwallowException_whenDlqPublishFails() {
            // given
            TemporaryChatCacheException exception =
                    temporaryCacheException();

            ChatRoomCacheInfoInvalidateEvent event =
                    new ChatRoomCacheInfoInvalidateEvent(
                            ROOM_ID
                    );

            doThrow(new RuntimeException("dlq publish failed"))
                    .when(dlqEventListPublishPort)
                    .publish(any(ChatRoomDlqEventList.class));

            // when & then
            assertThatCode(() -> sut.recover(exception, event, TX_ID))
                    .doesNotThrowAnyException();

            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatRoomDlqEventList.class));
        }
    }

    private ChatRoom chatRoom() {
        return ChatRoom.rehydrate(
                ROOM_ID,
                HOST_ID,
                TITLE,
                "description",
                category,
                Set.of(HOST_ID),
                0L,
                LocalDateTime.of(2026, 7, 7, 12, 0)
        );
    }

    private ChatRoom chatRoomWithLatest() {
        return ChatRoom.rehydrateWithLatest(
                ROOM_ID,
                HOST_ID,
                TITLE,
                "description",
                category,
                Set.of(HOST_ID, MEMBER_ID),
                10L,
                "message-1",
                "hello",
                Instant.parse("2026-07-07T03:00:00Z"),
                LocalDateTime.of(2026, 7, 7, 12, 0)
        );
    }

    private TemporaryChatPersistenceException temporaryPersistenceException() {
        TemporaryChatPersistenceException exception =
                mock(TemporaryChatPersistenceException.class);

        given(exception.getMessage()).willReturn(ERROR_MESSAGE);

        return exception;
    }

    private TemporaryChatCacheException temporaryCacheException() {
        TemporaryChatCacheException exception =
                mock(TemporaryChatCacheException.class);

        given(exception.getMessage()).willReturn(ERROR_MESSAGE);

        return exception;
    }
}
