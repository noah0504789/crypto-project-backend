package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.application.event.dlq.*;
import org.example.chat.chatroom.application.event.payload.ChatRoomPersistPayload;
import org.example.chat.chatroom.application.mapper.ChatRoomPayloadMapper;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomDlqServiceUnitTest {

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomCachePort cache;

    @InjectMocks
    private ChatRoomDlqService sut;

    private static final String ROOM_ID = "room-1";
    private static final String HOST_ID = "host-1";
    private static final String MEMBER_ID = "member-1";
    private static final String OLD_TITLE = "old-title";
    private static final String TITLE = "title";
    private static final String ERROR_MESSAGE = "dlq error";

    private final ChatRoomCategory category = ChatRoomCategory.FREE;

    @Nested
    @DisplayName("handle ChatRoomPersistedDlqEvent")
    class HandlePersistedDlqEventTest {

        @Test
        @DisplayName("payload를 ChatRoom 도메인으로 변환한 뒤 저장한다")
        void handle_shouldSaveChatRoom_whenPersistedDlqEvent() {
            // given
            ChatRoom domain = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    TITLE,
                    "description",
                    category,
                    Set.of(HOST_ID),
                    0L,
                    LocalDateTime.of(2026, 7, 7, 12, 0)
            );

            ChatRoomPersistPayload payload =
                    ChatRoomPayloadMapper.fromDomain(domain);

            ChatRoomPersistedDlqEvent event =
                    new ChatRoomPersistedDlqEvent(payload, ERROR_MESSAGE);

            // when
            sut.handle(event);

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
        }
    }

    @Nested
    @DisplayName("handle ChatRoomUpdatedDlqEvent")
    class HandleUpdatedDlqEventTest {

        @Test
        @DisplayName("수정 payload를 updateMap으로 변환해서 채팅방을 수정한다")
        void handle_shouldUpdateRoom_whenUpdatedDlqEvent() {
            // given
            ChatRoomUpdatedPayload updatedPayload = mock(ChatRoomUpdatedPayload.class);
            Map<String, Object> updateMap = Map.of("title", "new-title");

            given(updatedPayload.toUpdateMap()).willReturn(updateMap);

            ChatRoomUpdatedDlqEvent event =
                    new ChatRoomUpdatedDlqEvent(
                            ROOM_ID,
                            updatedPayload,
                            ERROR_MESSAGE
                    );

            // when
            sut.handle(event);

            // then
            then(persistence)
                    .should()
                    .updateRoomAndReturn(ROOM_ID, updateMap);

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handle ChatRoomDeletedDlqEvent")
    class HandleDeletedDlqEventTest {

        @Test
        @DisplayName("채팅방 id로 삭제한다")
        void handle_shouldDeleteRoom_whenDeletedDlqEvent() {
            // given
            ChatRoomDeletedDlqEvent event =
                    new ChatRoomDeletedDlqEvent(
                            ROOM_ID,
                            category,
                            ERROR_MESSAGE
                    );

            // when
            sut.handle(event);

            // then
            then(persistence)
                    .should()
                    .deleteById(ROOM_ID);

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handle ChatRoomJoinedDlqEvent")
    class HandleJoinedDlqEventTest {

        @Test
        @DisplayName("채팅방 멤버십을 추가한다")
        void handle_shouldJoinMembership_whenJoinedDlqEvent() {
            // given
            ChatRoomJoinedDlqEvent event =
                    new ChatRoomJoinedDlqEvent(
                            ROOM_ID,
                            MEMBER_ID,
                            ERROR_MESSAGE
                    );

            // when
            sut.handle(event);

            // then
            then(persistence)
                    .should()
                    .joinMembership(ROOM_ID, MEMBER_ID);

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handle ChatRoomLeavedDlqEvent")
    class HandleLeavedDlqEventTest {

        @Test
        @DisplayName("채팅방 멤버십을 제거한다")
        void handle_shouldLeaveMembership_whenLeavedDlqEvent() {
            // given
            ChatRoomLeavedDlqEvent event =
                    new ChatRoomLeavedDlqEvent(
                            ROOM_ID,
                            MEMBER_ID,
                            ERROR_MESSAGE
                    );

            // when
            sut.handle(event);

            // then
            then(persistence)
                    .should()
                    .leaveMembership(ROOM_ID, MEMBER_ID);

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handle ChatRoomActiveDlqEvent")
    class HandleActiveDlqEventTest {

        @Test
        @DisplayName("채팅방 멤버 활동 정보를 갱신한다")
        void handle_shouldActivateMembership_whenActiveDlqEvent() {
            // given
            Long lastMsgSeq = 10L;
            Long lastMsgMs = 100L;

            ChatRoomActiveDlqEvent event =
                    new ChatRoomActiveDlqEvent(
                            ROOM_ID,
                            MEMBER_ID,
                            lastMsgSeq,
                            lastMsgMs,
                            ERROR_MESSAGE
                    );

            // when
            sut.handle(event);

            // then
            then(persistence)
                    .should()
                    .activateMembership(
                            ROOM_ID,
                            MEMBER_ID,
                            lastMsgSeq,
                            lastMsgMs
                    );

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handle ChatRoomCacheSaveDlqEvent")
    class HandleCacheSaveDlqEventTest {

        @Test
        @DisplayName("최신 메시지를 포함한 채팅방을 조회해서 캐시를 warmUp 한다")
        void handle_shouldWarmUpCache_whenChatRoomExists() {
            // given
            ChatRoom domain = chatRoomWithLatest();

            ChatRoomCacheSaveDlqEvent event =
                    new ChatRoomCacheSaveDlqEvent(
                            ROOM_ID,
                            ERROR_MESSAGE
                    );

            given(persistence.findByIdWithLatestMessage(ROOM_ID))
                    .willReturn(Optional.of(domain));

            // when
            sut.handle(event);

            // then
            then(persistence)
                    .should()
                    .findByIdWithLatestMessage(ROOM_ID);

            then(cache)
                    .should()
                    .warmUp(domain);
        }

        @Test
        @DisplayName("채팅방이 없으면 캐시 warmUp을 수행하지 않는다")
        void handle_shouldNotWarmUpCache_whenChatRoomDoesNotExist() {
            // given
            ChatRoomCacheSaveDlqEvent event =
                    new ChatRoomCacheSaveDlqEvent(
                            ROOM_ID,
                            ERROR_MESSAGE
                    );

            given(persistence.findByIdWithLatestMessage(ROOM_ID))
                    .willReturn(Optional.empty());

            // when
            sut.handle(event);

            // then
            then(persistence)
                    .should()
                    .findByIdWithLatestMessage(ROOM_ID);

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handle ChatRoomCacheUpdateDlqEvent")
    class HandleCacheUpdateDlqEventTest {

        @Test
        @DisplayName("최신 메시지를 포함한 채팅방을 조회해서 캐시 수정 복구를 수행한다")
        void handle_shouldRecoverRoomUpdate_whenChatRoomExists() {
            // given
            ChatRoom domain = chatRoomWithLatest();

            ChatRoomCacheUpdateDlqEvent event =
                    new ChatRoomCacheUpdateDlqEvent(
                            ROOM_ID,
                            OLD_TITLE,
                            ERROR_MESSAGE
                    );

            given(persistence.findByIdWithLatestMessage(ROOM_ID))
                    .willReturn(Optional.of(domain));

            // when
            sut.handle(event);

            // then
            then(persistence)
                    .should()
                    .findByIdWithLatestMessage(ROOM_ID);

            then(cache)
                    .should()
                    .recoverRoomUpdate(domain, OLD_TITLE);
        }

        @Test
        @DisplayName("채팅방이 없으면 캐시 수정 복구를 수행하지 않는다")
        void handle_shouldNotRecoverRoomUpdate_whenChatRoomDoesNotExist() {
            // given
            ChatRoomCacheUpdateDlqEvent event =
                    new ChatRoomCacheUpdateDlqEvent(
                            ROOM_ID,
                            OLD_TITLE,
                            ERROR_MESSAGE
                    );

            given(persistence.findByIdWithLatestMessage(ROOM_ID))
                    .willReturn(Optional.empty());

            // when
            sut.handle(event);

            // then
            then(persistence)
                    .should()
                    .findByIdWithLatestMessage(ROOM_ID);

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handle ChatRoomCacheDeleteDlqEvent")
    class HandleCacheDeleteDlqEventTest {

        @Test
        @DisplayName("캐시에서 채팅방을 삭제한다")
        void handle_shouldDeleteRoomFromCache_whenCacheDeleteDlqEvent() {
            // given
            Set<String> memberIds = Set.of(HOST_ID, MEMBER_ID);

            ChatRoomCacheDeleteDlqEvent event =
                    new ChatRoomCacheDeleteDlqEvent(
                            ROOM_ID,
                            category,
                            TITLE,
                            memberIds,
                            ERROR_MESSAGE
                    );

            // when
            sut.handle(event);

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
        }
    }

    @Nested
    @DisplayName("handle ChatRoomCacheActivityInvalidateDlqEvent")
    class HandleCacheActivityInvalidateDlqEventTest {

        @Test
        @DisplayName("멤버 활동 캐시를 무효화한다")
        void handle_shouldInvalidateMembershipActivity_whenCacheActivityInvalidateDlqEvent() {
            // given
            ChatRoomCacheActivityInvalidateDlqEvent event =
                    new ChatRoomCacheActivityInvalidateDlqEvent(
                            ROOM_ID,
                            MEMBER_ID,
                            ERROR_MESSAGE
                    );

            // when
            sut.handle(event);

            // then
            then(cache)
                    .should()
                    .invalidateMembershipActivity(ROOM_ID, MEMBER_ID);

            then(persistence)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handle ChatRoomCacheInfoInvalidateDlqEvent")
    class HandleCacheInfoInvalidateDlqEventTest {

        @Test
        @DisplayName("채팅방 정보 캐시를 무효화한다")
        void handle_shouldInvalidateRoomInfo_whenCacheInfoInvalidateDlqEvent() {
            // given
            ChatRoomCacheInfoInvalidateDlqEvent event =
                    new ChatRoomCacheInfoInvalidateDlqEvent(
                            ROOM_ID,
                            ERROR_MESSAGE
                    );

            // when
            sut.handle(event);

            // then
            then(cache)
                    .should()
                    .invalidateRoomInfo(ROOM_ID);

            then(persistence)
                    .shouldHaveNoInteractions();
        }
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
}