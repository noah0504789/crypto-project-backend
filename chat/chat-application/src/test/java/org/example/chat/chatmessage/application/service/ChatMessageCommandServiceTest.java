package org.example.chat.chatmessage.application.service;

import org.example.chat.common.exception.ChatMessageCacheException;
import org.example.chat.chatmessage.application.service.command.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.service.result.ChatMessageSaveResult;
import org.example.chat.chatmessage.domain.event.dlq.ChatMessageDlqEventList;
import org.example.chat.chatmessage.domain.event.ChatMessageEventList;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.service.result.ChatRoomMembershipScore;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.exception.ChatRoomMembershipNotFoundException;
import org.example.chat.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.common.exception.ChatMessagePersistException;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.common.time.ServiceZoneUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageCommandServiceTest {

    @Mock
    private ChatMessagePersistencePort chatMessagePersistencePort;

    @Mock
    private ChatRoomPersistencePort chatRoomPersistencePort;

    @Mock
    private ChatMessageCachePort chatMessageCachePort;

    @Mock
    private OutboxEventListPublishPort outboxEventListPublishPort;

    @InjectMocks
    private ChatMessageCommandService sut;

    private final String messageId = "100000000000000000000001";
    private final String roomId = "000000000000000000000001";
    private final String writerId = "writer-1";
    private final String content = "hello";
    private final String clientMessageId = "client-msg-1";

    private final Instant latestCreatedAt = Instant.parse("2026-01-01T03:00:00Z");
    private final long latestCreatedAtMillis = latestCreatedAt.toEpochMilli();

    private final String memberId1 = "member-1";
    private final String memberId2 = "member-2";

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("채팅방이 존재하고 작성자가 멤버이면 outbox 이벤트를 발행하고 cache에 저장한 뒤 결과를 반환한다")
        void saveSuccess() {
            // given
            ChatRoom chatRoom = chatRoom(Set.of(writerId, "user-2"));

            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            // when
            ChatMessageSaveResult result = sut.save(command);

            // then
            assertThat(result.id()).isEqualTo(messageId);
            assertThat(result.ts()).isGreaterThan(0L);

            ArgumentCaptor<AbstractOutboxEventList> eventListCaptor =
                    ArgumentCaptor.forClass(AbstractOutboxEventList.class);
            ArgumentCaptor<ChatMessage> messageCaptor =
                    ArgumentCaptor.forClass(ChatMessage.class);

            verify(chatRoomPersistencePort).findById(roomId);
            verify(outboxEventListPublishPort).publish(eventListCaptor.capture());
            verify(chatMessageCachePort).save(
                    messageCaptor.capture(),
                    eq(chatRoom.getCategory()),
                    eq(chatRoom.getMemberIds())
            );

            verify(chatMessagePersistencePort, never()).save(any(ChatMessage.class));

            AbstractOutboxEventList publishedEventList = eventListCaptor.getValue();

            assertThat(publishedEventList.getEventList()).hasSize(3);

            ChatMessage cachedMessage = messageCaptor.getValue();

            assertThat(cachedMessage.getId()).isEqualTo(messageId);
            assertThat(cachedMessage.getRoomId()).isEqualTo(roomId);
            assertThat(cachedMessage.getWriterId()).isEqualTo(writerId);
            assertThat(cachedMessage.getContent()).isEqualTo(content);
        }

        @Test
        @DisplayName("채팅방이 없으면 ChatRoomNotFoundException을 던지고 outbox 발행과 cache 저장을 수행하지 않는다")
        void saveThrowsWhenChatRoomNotFound() {
            // given
            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatRoomNotFoundException.class);

            verify(chatRoomPersistencePort).findById(roomId);
            verify(outboxEventListPublishPort, never()).publish(any(AbstractOutboxEventList.class));
            verify(chatMessageCachePort, never())
                    .save(any(ChatMessage.class), any(ChatRoomCategory.class), anySet());
            verify(chatMessagePersistencePort, never()).save(any(ChatMessage.class));
        }

        @Test
        @DisplayName("작성자가 채팅방 멤버가 아니면 ChatRoomMembershipNotFoundException을 던지고 outbox 발행과 cache 저장을 수행하지 않는다")
        void saveThrowsWhenWriterIsNotMember() {
            // given
            ChatRoom chatRoom = chatRoom(Set.of("other-user"));

            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatRoomMembershipNotFoundException.class);

            verify(chatRoomPersistencePort).findById(roomId);
            verify(outboxEventListPublishPort, never()).publish(any(AbstractOutboxEventList.class));
            verify(chatMessageCachePort, never())
                    .save(any(ChatMessage.class), any(ChatRoomCategory.class), anySet());
            verify(chatMessagePersistencePort, never()).save(any(ChatMessage.class));
        }

        @Test
        @DisplayName("outbox 발행 중 TemporaryOutboxPersistenceException이 발생하면 그대로 전파하고 cache 저장을 수행하지 않는다")
        void saveThrowsTemporaryOutboxPersistenceExceptionWhenOutboxPublishTemporarilyFails() {
            // given
            ChatRoom chatRoom = chatRoom(Set.of(writerId, "user-2"));
            TemporaryOutboxPersistenceException exception = new TemporaryOutboxPersistenceException("temporary outbox failure", new RuntimeException());

            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(any(AbstractOutboxEventList.class));

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isSameAs(exception);

            verify(chatRoomPersistencePort).findById(roomId);
            verify(outboxEventListPublishPort).publish(any(AbstractOutboxEventList.class));
            verify(chatMessageCachePort, never())
                    .save(any(ChatMessage.class), any(ChatRoomCategory.class), anySet());
            verify(chatMessagePersistencePort, never()).save(any(ChatMessage.class));
        }

        @Test
        @DisplayName("outbox 발행 중 일반 예외가 발생하면 ChatMessagePersistException으로 감싸고 cache 저장을 수행하지 않는다")
        void saveThrowsChatMessagePersistExceptionWhenOutboxPublishFails() {
            // given
            ChatRoom chatRoom = chatRoom(Set.of(writerId, "user-2"));
            RuntimeException exception = new RuntimeException("outbox publish failed");

            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(any(AbstractOutboxEventList.class));

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatMessagePersistException.class)
                    .hasMessageContaining("failed during chat message persist event publish")
                    .hasCause(exception);

            verify(chatRoomPersistencePort).findById(roomId);
            verify(outboxEventListPublishPort).publish(any(AbstractOutboxEventList.class));
            verify(chatMessageCachePort, never())
                    .save(any(ChatMessage.class), any(ChatRoomCategory.class), anySet());
            verify(chatMessagePersistencePort, never()).save(any(ChatMessage.class));
        }

        @Test
        @DisplayName("cache 저장 중 예외가 발생하면 ChatMessageCacheException을 던진다")
        void saveThrowsChatMessageCacheExceptionWhenCacheSaveFails() {
            // given
            ChatRoom chatRoom = chatRoom(Set.of(writerId, "user-2"));
            RuntimeException exception = new RuntimeException("cache save failed");

            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            doThrow(exception)
                    .when(chatMessageCachePort)
                    .save(any(ChatMessage.class), eq(chatRoom.getCategory()), eq(chatRoom.getMemberIds()));

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatMessageCacheException.class)
                    .hasMessageContaining("failed during cache save")
                    .hasCause(exception);

            verify(chatRoomPersistencePort).findById(roomId);
            verify(outboxEventListPublishPort).publish(any(AbstractOutboxEventList.class));
            verify(chatMessageCachePort)
                    .save(any(ChatMessage.class), eq(chatRoom.getCategory()), eq(chatRoom.getMemberIds()));
            verify(chatMessagePersistencePort, never()).save(any(ChatMessage.class));
        }
    }

    @Nested
    @DisplayName("hardDelete")
    class HardDeleteTest {

        @Test
        @DisplayName("메시지 hardDelete 성공 시 msgCnt 감소, membership score 갱신, cache 삭제를 수행한다")
        void hardDeleteSuccess() {
            // given
            ChatMessage latestMessage = chatMessage("100000000000000000000002", latestCreatedAt);

            List<ChatRoomMembershipScore> chatRoomMembershipScores = List.of(
                    new ChatRoomMembershipScore(memberId1, latestCreatedAtMillis),
                    new ChatRoomMembershipScore(memberId2, 0L)
            );

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestExcluding(roomId, messageId))
                    .willReturn(Optional.of(latestMessage));

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, latestCreatedAtMillis))
                    .willReturn(chatRoomMembershipScores);

            // when
            sut.hardDelete(messageId, roomId);

            // then
            verify(chatMessagePersistencePort).hardDelete(messageId);
            verify(chatRoomPersistencePort).decrementMsgCnt(roomId);
            verify(chatMessagePersistencePort).findLatestExcluding(roomId, messageId);
            verify(chatRoomPersistencePort).refreshMembershipScores(roomId, latestCreatedAtMillis);
            verify(chatMessageCachePort).hardDelete(messageId, roomId, chatRoomMembershipScores);
        }

        @Test
        @DisplayName("Mongo에서 삭제할 메시지가 없으면 이후 작업을 수행하지 않는다")
        void hardDeleteSkippedWhenMongoMessageNotFound() {
            // given
            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(false);

            // when
            sut.hardDelete(messageId, roomId);

            // then
            verify(chatMessagePersistencePort).hardDelete(messageId);

            verify(chatRoomPersistencePort, never()).decrementMsgCnt(anyString());
            verify(chatMessagePersistencePort, never()).findLatestExcluding(anyString(), anyString());
            verify(chatRoomPersistencePort, never()).refreshMembershipScores(anyString(), anyLong());
            verify(chatMessageCachePort, never()).hardDelete(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("삭제 후 남은 최신 메시지가 없으면 fallbackMsgCreatedAt=0으로 membership score를 갱신한다")
        void hardDeleteWithNoLatestMessage() {
            // given
            List<ChatRoomMembershipScore> chatRoomMembershipScores = List.of(
                    new ChatRoomMembershipScore(memberId1, 0L),
                    new ChatRoomMembershipScore(memberId2, 0L)
            );

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestExcluding(roomId, messageId))
                    .willReturn(Optional.empty());

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, 0L))
                    .willReturn(chatRoomMembershipScores);

            // when
            sut.hardDelete(messageId, roomId);

            // then
            verify(chatMessagePersistencePort).hardDelete(messageId);
            verify(chatRoomPersistencePort).decrementMsgCnt(roomId);
            verify(chatMessagePersistencePort).findLatestExcluding(roomId, messageId);
            verify(chatRoomPersistencePort).refreshMembershipScores(roomId, 0L);
            verify(chatMessageCachePort).hardDelete(messageId, roomId, chatRoomMembershipScores);
        }

        @Test
        @DisplayName("cache hardDelete가 실패해도 예외를 전파하지 않는다")
        void hardDeleteCacheFailsButDoesNotThrow() {
            // given
            ChatMessage latestMessage = chatMessage("100000000000000000000002", latestCreatedAt);

            List<ChatRoomMembershipScore> chatRoomMembershipScores = List.of(
                    new ChatRoomMembershipScore(memberId1, latestCreatedAtMillis)
            );

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestExcluding(roomId, messageId))
                    .willReturn(Optional.of(latestMessage));

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, latestCreatedAtMillis))
                    .willReturn(chatRoomMembershipScores);

            doThrow(new RuntimeException("redis delete failed"))
                    .when(chatMessageCachePort)
                    .hardDelete(messageId, roomId, chatRoomMembershipScores);

            // when & then
            assertThatCode(() -> sut.hardDelete(messageId, roomId))
                    .doesNotThrowAnyException();

            verify(chatMessagePersistencePort).hardDelete(messageId);
            verify(chatRoomPersistencePort).decrementMsgCnt(roomId);
            verify(chatMessagePersistencePort).findLatestExcluding(roomId, messageId);
            verify(chatRoomPersistencePort).refreshMembershipScores(roomId, latestCreatedAtMillis);
            verify(chatMessageCachePort).hardDelete(messageId, roomId, chatRoomMembershipScores);
        }

        @Test
        @DisplayName("Mongo hardDelete 중 예외가 발생하면 예외를 전파한다")
        void hardDeleteThrowsWhenMongoDeleteFails() {
            // given
            RuntimeException exception = new RuntimeException("mongo hardDelete failed");

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.hardDelete(messageId, roomId))
                    .isSameAs(exception);

            verify(chatMessagePersistencePort).hardDelete(messageId);
            verify(chatRoomPersistencePort, never()).decrementMsgCnt(anyString());
            verify(chatMessagePersistencePort, never()).findLatestExcluding(anyString(), anyString());
            verify(chatRoomPersistencePort, never()).refreshMembershipScores(anyString(), anyLong());
            verify(chatMessageCachePort, never()).hardDelete(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("msgCnt 감소 중 예외가 발생하면 이후 작업을 수행하지 않고 예외를 전파한다")
        void hardDeleteThrowsWhenDecrementMsgCntFails() {
            // given
            RuntimeException exception = new RuntimeException("decrement failed");

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            doThrow(exception)
                    .when(chatRoomPersistencePort)
                    .decrementMsgCnt(roomId);

            // when & then
            assertThatThrownBy(() -> sut.hardDelete(messageId, roomId))
                    .isSameAs(exception);

            verify(chatMessagePersistencePort).hardDelete(messageId);
            verify(chatRoomPersistencePort).decrementMsgCnt(roomId);

            verify(chatMessagePersistencePort, never()).findLatestExcluding(anyString(), anyString());
            verify(chatRoomPersistencePort, never()).refreshMembershipScores(anyString(), anyLong());
            verify(chatMessageCachePort, never()).hardDelete(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("membership score refresh 중 예외가 발생하면 cache 삭제를 수행하지 않고 예외를 전파한다")
        void hardDeleteThrowsWhenRefreshMembershipScoresFails() {
            // given
            ChatMessage latestMessage = chatMessage("100000000000000000000002", latestCreatedAt);
            RuntimeException exception = new RuntimeException("refresh membership failed");

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestExcluding(roomId, messageId))
                    .willReturn(Optional.of(latestMessage));

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, latestCreatedAtMillis))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.hardDelete(messageId, roomId))
                    .isSameAs(exception);

            verify(chatMessagePersistencePort).hardDelete(messageId);
            verify(chatRoomPersistencePort).decrementMsgCnt(roomId);
            verify(chatMessagePersistencePort).findLatestExcluding(roomId, messageId);
            verify(chatRoomPersistencePort).refreshMembershipScores(roomId, latestCreatedAtMillis);

            verify(chatMessageCachePort, never()).hardDelete(anyString(), anyString(), anyList());
        }
    }

    private ChatRoom chatRoom(Set<String> memberIds) {
        return ChatRoom.rehydrate(
                roomId,
                "host-1",
                "테스트방",
                "테스트 설명",
                ChatRoomCategory.FREE,
                memberIds,
                0L,
                LocalDateTime.now()
        );
    }

    private ChatMessage chatMessage(String id, Instant createdAt) {
        return ChatMessage.builder()
                .id(id)
                .roomId(roomId)
                .writerId(writerId)
                .content(content)
                .createdAt(LocalDateTime.ofInstant(createdAt, ServiceZoneUtils.ZONE_ID))
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }
}