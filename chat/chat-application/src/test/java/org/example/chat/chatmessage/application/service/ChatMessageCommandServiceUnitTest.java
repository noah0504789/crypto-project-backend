package org.example.chat.chatmessage.application.service;

import org.example.chat.chatmessage.application.service.command.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.service.result.ChatMessageSaveResult;
import org.example.chat.chatmessage.application.event.ChatMessageEventList;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.service.result.ChatRoomMembershipScore;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.exception.ChatRoomMembershipNotFoundException;
import org.example.chat.chatroom.application.exception.ChatRoomNotFoundException;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatmessage.application.exception.ChatMessagePersistException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageCommandServiceUnitTest {

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
    private final long latestCreatedAtMs = latestCreatedAt.toEpochMilli();

    private final String memberId1 = "member-1";
    private final String memberId2 = "member-2";

    private final ChatRoomCategory category = ChatRoomCategory.FREE;

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("채팅방이 존재하고 작성 가능한 멤버이면 메시지 이벤트를 발행하고 캐시에 저장한 뒤 결과를 반환한다")
        void save_shouldPublishEventsSaveCacheAndReturnResult() {
            // given
            ChatMessageSaveCommand command = saveCommand();

            ChatRoom chatRoom = chatRoomWithMembers(writerId, memberId1, memberId2);

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            // when
            ChatMessageSaveResult result = sut.save(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(messageId);
            assertThat(result.ts()).isPositive();

            InOrder inOrder = inOrder(
                    chatRoomPersistencePort,
                    outboxEventListPublishPort,
                    chatMessageCachePort
            );

            inOrder.verify(chatRoomPersistencePort)
                    .findById(roomId);

            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatMessageEventList.class));

            inOrder.verify(chatMessageCachePort)
                    .save(
                            argThat(message ->
                                    message.getId().equals(messageId)
                                            && message.getRoomId().equals(roomId)
                                            && message.getWriterId().equals(writerId)
                                            && message.getContent().equals(content)
                            ),
                            eq(chatRoom.getMemberIds())
                    );

            then(chatMessagePersistencePort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("채팅방이 없으면 ChatRoomNotFoundException이 발생하고 이벤트와 캐시 저장은 수행하지 않는다")
        void save_shouldThrow_whenChatRoomNotFound() {
            // given
            ChatMessageSaveCommand command = saveCommand();

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatRoomNotFoundException.class);

            then(chatRoomPersistencePort)
                    .should()
                    .findById(roomId);

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(chatMessageCachePort)
                    .shouldHaveNoInteractions();

            then(chatMessagePersistencePort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("작성자가 채팅방 멤버가 아니면 ChatRoomMembershipNotFoundException이 발생하고 이벤트와 캐시 저장은 수행하지 않는다")
        void save_shouldThrow_whenWriterIsNotMember() {
            // given
            ChatMessageSaveCommand command = saveCommand();

            ChatRoom chatRoom = chatRoomWithMembers(memberId1, memberId2);

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatRoomMembershipNotFoundException.class);

            then(chatRoomPersistencePort)
                    .should()
                    .findById(roomId);

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(chatMessageCachePort)
                    .shouldHaveNoInteractions();

            then(chatMessagePersistencePort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Outbox 일시 장애가 발생하면 TemporaryOutboxPersistenceException을 그대로 전파하고 캐시는 저장하지 않는다")
        void save_shouldRethrowTemporaryOutboxException() {
            // given
            ChatMessageSaveCommand command = saveCommand();

            ChatRoom chatRoom = chatRoomWithMembers(writerId, memberId1);

            TemporaryOutboxPersistenceException exception =
                    new TemporaryOutboxPersistenceException(
                            "temporary outbox failure",
                            new RuntimeException("temporary")
                    );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(any(ChatMessageEventList.class));

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isSameAs(exception);

            then(outboxEventListPublishPort)
                    .should()
                    .publish(any(ChatMessageEventList.class));

            then(chatMessageCachePort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Outbox 일반 장애가 발생하면 ChatMessagePersistException으로 감싸서 전파하고 캐시는 저장하지 않는다")
        void save_shouldWrapUnexpectedOutboxException() {
            // given
            ChatMessageSaveCommand command = saveCommand();

            ChatRoom chatRoom = chatRoomWithMembers(writerId, memberId1);

            RuntimeException exception = new RuntimeException("outbox failed");

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(any(ChatMessageEventList.class));

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatMessagePersistException.class)
                    .hasMessageContaining("failed during chat message persist event publish")
                    .hasCause(exception);

            then(outboxEventListPublishPort)
                    .should()
                    .publish(any(ChatMessageEventList.class));

            then(chatMessageCachePort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("캐시 저장 실패는 로그만 남기고 예외를 전파하지 않으며 저장은 정상 처리된다")
        void save_shouldSwallowCacheFailure_whenCacheSaveFails() {
            // given
            ChatMessageSaveCommand command = saveCommand();

            ChatRoom chatRoom = chatRoomWithMembers(writerId, memberId1);

            RuntimeException exception = new RuntimeException("cache save failed");

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            doThrow(exception)
                    .when(chatMessageCachePort)
                    .save(any(ChatMessage.class), eq(chatRoom.getMemberIds()));

            // when & then: 캐시 실패해도 예외 전파 없이 정상 반환(조회 repair 가 흡수)
            assertThatCode(() -> sut.save(command))
                    .doesNotThrowAnyException();

            InOrder inOrder = inOrder(
                    chatRoomPersistencePort,
                    outboxEventListPublishPort,
                    chatMessageCachePort
            );

            inOrder.verify(chatRoomPersistencePort)
                    .findById(roomId);

            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatMessageEventList.class));

            inOrder.verify(chatMessageCachePort)
                    .save(any(ChatMessage.class), eq(chatRoom.getMemberIds()));
        }
    }

    @Nested
    @DisplayName("hardDelete")
    class HardDeleteTest {

        @Test
        @DisplayName("메시지 삭제에 성공하면 방 메시지 수를 감소시키고 최신 메시지 기준으로 멤버십 점수를 갱신한 뒤 캐시에서 삭제한다")
        void hardDelete_shouldDeleteMessageRefreshScoresAndDeleteCache() {
            // given
            ChatMessage latestMessage = latestMessage();

            List<ChatRoomMembershipScore> scores = List.of(
                    mock(ChatRoomMembershipScore.class),
                    mock(ChatRoomMembershipScore.class)
            );

            given(chatMessagePersistencePort.hardDeleteById(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestMessageExcluding(roomId, messageId))
                    .willReturn(Optional.of(latestMessage));

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, latestCreatedAtMs))
                    .willReturn(scores);

            // when
            sut.hardDelete(messageId, roomId);

            // then
            InOrder inOrder = inOrder(
                    chatMessagePersistencePort,
                    chatRoomPersistencePort,
                    chatMessageCachePort
            );

            inOrder.verify(chatMessagePersistencePort)
                    .hardDeleteById(messageId);

            inOrder.verify(chatRoomPersistencePort)
                    .decrementMessageCount(roomId);

            inOrder.verify(chatMessagePersistencePort)
                    .findLatestMessageExcluding(roomId, messageId);

            inOrder.verify(chatRoomPersistencePort)
                    .refreshMembershipScores(roomId, latestCreatedAtMs);

            inOrder.verify(chatMessageCachePort)
                    .hardDelete(messageId, roomId, scores);

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("메시지가 존재하지 않아 삭제되지 않으면 이후 작업을 수행하지 않는다")
        void hardDelete_shouldDoNothing_whenMessageWasNotDeleted() {
            // given
            given(chatMessagePersistencePort.hardDeleteById(messageId))
                    .willReturn(false);

            // when
            sut.hardDelete(messageId, roomId);

            // then
            then(chatMessagePersistencePort)
                    .should()
                    .hardDeleteById(messageId);

            then(chatRoomPersistencePort)
                    .shouldHaveNoMoreInteractions();

            then(chatMessageCachePort)
                    .shouldHaveNoInteractions();

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(chatMessagePersistencePort)
                    .should(never())
                    .findLatestMessageExcluding(any(), any());
        }

        @Test
        @DisplayName("삭제 후 남은 최신 메시지가 없으면 fallbackMsgCreatedAt을 0으로 점수 갱신한다")
        void hardDelete_shouldUseZeroFallbackMsgCreatedAt_whenLatestMessageDoesNotExist() {
            // given
            List<ChatRoomMembershipScore> scores = List.of(
                    mock(ChatRoomMembershipScore.class)
            );

            given(chatMessagePersistencePort.hardDeleteById(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestMessageExcluding(roomId, messageId))
                    .willReturn(Optional.empty());

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, 0L))
                    .willReturn(scores);

            // when
            sut.hardDelete(messageId, roomId);

            // then
            InOrder inOrder = inOrder(
                    chatMessagePersistencePort,
                    chatRoomPersistencePort,
                    chatMessageCachePort
            );

            inOrder.verify(chatMessagePersistencePort)
                    .hardDeleteById(messageId);

            inOrder.verify(chatRoomPersistencePort)
                    .decrementMessageCount(roomId);

            inOrder.verify(chatMessagePersistencePort)
                    .findLatestMessageExcluding(roomId, messageId);

            inOrder.verify(chatRoomPersistencePort)
                    .refreshMembershipScores(roomId, 0L);

            inOrder.verify(chatMessageCachePort)
                    .hardDelete(messageId, roomId, scores);
        }

        @Test
        @DisplayName("캐시 hardDelete 실패는 로그만 남기고 예외를 전파하지 않는다")
        void hardDelete_shouldSwallowException_whenCacheHardDeleteFails() {
            // given
            ChatMessage latestMessage = latestMessage();

            List<ChatRoomMembershipScore> scores = List.of(
                    mock(ChatRoomMembershipScore.class)
            );

            given(chatMessagePersistencePort.hardDeleteById(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestMessageExcluding(roomId, messageId))
                    .willReturn(Optional.of(latestMessage));

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, latestCreatedAtMs))
                    .willReturn(scores);

            doThrow(new RuntimeException("cache hard delete failed"))
                    .when(chatMessageCachePort)
                    .hardDelete(messageId, roomId, scores);

            // when & then
            assertThatCode(() -> sut.hardDelete(messageId, roomId))
                    .doesNotThrowAnyException();

            then(chatMessageCachePort)
                    .should()
                    .hardDelete(messageId, roomId, scores);
        }

        @Test
        @DisplayName("hardDeleteById에서 TemporaryChatPersistenceException이 발생하면 그대로 전파한다")
        void hardDelete_shouldRethrowTemporaryChatPersistenceException_whenDeleteFailsTemporarily() {
            // given
            TemporaryChatPersistenceException exception =
                    temporaryChatPersistenceException();

            given(chatMessagePersistencePort.hardDeleteById(messageId))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.hardDelete(messageId, roomId))
                    .isSameAs(exception);

            then(chatRoomPersistencePort)
                    .shouldHaveNoInteractions();

            then(chatMessageCachePort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("decrementMessageCount에서 TemporaryChatPersistenceException이 발생하면 그대로 전파하고 이후 작업은 수행하지 않는다")
        void hardDelete_shouldRethrowTemporaryChatPersistenceException_whenDecrementFailsTemporarily() {
            // given
            TemporaryChatPersistenceException exception =
                    temporaryChatPersistenceException();

            given(chatMessagePersistencePort.hardDeleteById(messageId))
                    .willReturn(true);

            doThrow(exception)
                    .when(chatRoomPersistencePort)
                    .decrementMessageCount(roomId);

            // when & then
            assertThatThrownBy(() -> sut.hardDelete(messageId, roomId))
                    .isSameAs(exception);

            InOrder inOrder = inOrder(
                    chatMessagePersistencePort,
                    chatRoomPersistencePort
            );

            inOrder.verify(chatMessagePersistencePort)
                    .hardDeleteById(messageId);

            inOrder.verify(chatRoomPersistencePort)
                    .decrementMessageCount(roomId);

            then(chatMessagePersistencePort)
                    .should(never())
                    .findLatestMessageExcluding(any(), any());

            then(chatMessageCachePort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("recover는 hardDelete 재시도 소진 시 예외를 밖으로 던지지 않는다")
        void recover_shouldNotThrow() {
            // given
            TemporaryChatPersistenceException exception =
                    temporaryChatPersistenceException();

            // when & then
            assertThatCode(() -> sut.recover(exception, messageId, roomId))
                    .doesNotThrowAnyException();

            then(chatMessagePersistencePort)
                    .shouldHaveNoInteractions();

            then(chatRoomPersistencePort)
                    .shouldHaveNoInteractions();

            then(chatMessageCachePort)
                    .shouldHaveNoInteractions();

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();
        }
    }

    private ChatMessageSaveCommand saveCommand() {
        return new ChatMessageSaveCommand(
                messageId,
                roomId,
                writerId,
                content,
                clientMessageId
        );
    }

    private ChatRoom chatRoomWithMembers(String... memberIds) {
        return ChatRoom.rehydrate(
                roomId,
                "host-1",
                "room-title",
                "description",
                category,
                Set.of(memberIds),
                10L,
                LocalDateTime.of(2026, 1, 1, 12, 0)
        );
    }

    private ChatMessage latestMessage() {
        return ChatMessage.rehydrate(
                "latest-message-id",
                roomId,
                memberId1,
                "latest message",
                latestCreatedAt
        );
    }

    private TemporaryChatPersistenceException temporaryChatPersistenceException() {
        return new TemporaryChatPersistenceException(
                "temporary persistence failure",
                new RuntimeException("temporary")
        );
    }
}