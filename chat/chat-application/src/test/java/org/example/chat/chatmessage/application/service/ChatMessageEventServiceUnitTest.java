package org.example.chat.chatmessage.application.service;

import org.example.chat.chatmessage.application.exception.DuplicateChatMessageException;
import org.example.chat.chatmessage.application.event.dlq.ChatMessageDlqEventList;
import org.example.chat.chatmessage.application.mapper.ChatMessagePayloadMapper;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.application.port.out.ChatMessageMetricsPort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatmessage.application.event.ChatMessagePersistEvent;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.dlq.application.port.out.DlqEventListPublishPort;
import org.example.contract.chatmessage.ChatMessagePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageEventServiceUnitTest {

    @Mock
    private ChatMessagePersistencePort chatMessagePersistencePort;

    @Mock
    private ChatRoomPersistencePort chatRoomPersistencePort;

    @Mock
    private DlqEventListPublishPort dlqEventListPublishPort;

    @Mock
    private ChatMessageMetricsPort metrics;

    private ChatMessageEventService sut;

    private final String txId = "tx-1";

    private final String messageId = "100000000000000000000001";
    private final String roomId = "000000000000000000000001";
    private final String writerId = "writer-1";
    private final String content = "hello";

    private final String memberId1 = "member-1";
    private final String memberId2 = "member-2";
    private final Set<String> memberIds = Set.of(memberId1, memberId2);

    private final Instant createdAt = Instant.parse("2026-01-01T01:00:00Z");

    @BeforeEach
    void setUp() {
        sut = new ChatMessageEventService(
                chatMessagePersistencePort,
                chatRoomPersistencePort,
                dlqEventListPublishPort,
                metrics
        );
    }

    @Nested
    @DisplayName("handle ChatMessagePersistEvent")
    class HandleChatMessagePersistEventTest {

        @BeforeEach
        void setUpMetrics() {
            lenient().doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
        }).when(metrics).recordMessageInsert(any(Runnable.class));
            lenient().doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(metrics).recordRoomCounter(any(Runnable.class));
            lenient().doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(metrics).recordMembership(any(Runnable.class));
        }

        @Test
        @DisplayName("채팅 메시지 저장 이벤트를 처리하면 메시지를 저장하고 채팅방 메시지 수와 멤버십 점수를 갱신한다")
        void handle_shouldSaveMessageAndUpdateRoomState_whenPersistEvent() {
            // given
            ChatMessage message = chatMessage();
            ChatMessagePayload payload = ChatMessagePayloadMapper.fromDomain(message);

            ChatMessagePersistEvent event = new ChatMessagePersistEvent(
                    payload,
                    memberIds
            );

            // when
            sut.handle(event, txId);

            // then
            InOrder inOrder = inOrder(
                    chatMessagePersistencePort,
                    chatRoomPersistencePort
            );

            inOrder.verify(chatMessagePersistencePort)
                    .save(argThat(saved ->
                            saved.getId().equals(messageId)
                                    && saved.getRoomId().equals(roomId)
                                    && saved.getWriterId().equals(writerId)
                                    && saved.getContent().equals(content)
                    ));

            inOrder.verify(chatRoomPersistencePort)
                    .incrementMessageCount(roomId, 1);

            inOrder.verify(chatRoomPersistencePort)
                    .updateMembershipScores(
                            eq(roomId),
                            eq(memberIds),
                            eq(message.createdAtEpochMillis())
                    );
        }

        @Test
        @DisplayName("메시지 저장 중 예외가 발생하면 이후 채팅방 갱신 작업을 수행하지 않고 예외를 전파한다")
        void handle_shouldStopWhenMessageSaveFails() {
            // given
            RuntimeException exception = new RuntimeException("message save failed");

            ChatMessage message = chatMessage();
            ChatMessagePayload payload = ChatMessagePayloadMapper.fromDomain(message);

            ChatMessagePersistEvent event = new ChatMessagePersistEvent(
                    payload,
                    memberIds
            );

            doThrow(exception)
                    .when(chatMessagePersistencePort)
                    .save(any(ChatMessage.class));

            // when & then
            assertThatThrownBy(() -> sut.handle(event, txId))
                    .isSameAs(exception);

            then(chatMessagePersistencePort)
                    .should()
                    .save(any(ChatMessage.class));

            then(chatRoomPersistencePort)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("재시도 가능한 저장 예외가 발생하면 실패 횟수를 기록하고 예외를 전파한다")
        void handle_shouldRecordRetryableFailure_whenTemporaryPersistenceFails() {
            // given
            TemporaryChatPersistenceException exception = new TemporaryChatPersistenceException(
                    "temporary message save failure",
                    new RuntimeException("mongo unavailable")
            );
            ChatMessagePayload payload = ChatMessagePayloadMapper.fromDomain(chatMessage());
            ChatMessagePersistEvent event = new ChatMessagePersistEvent(payload, memberIds);

            doThrow(exception)
                    .when(chatMessagePersistencePort)
                    .save(any(ChatMessage.class));

            // when & then
            assertThatThrownBy(() -> sut.handle(event, txId))
                    .isSameAs(exception);

            then(metrics).should().recordRetryableFailure();
        }

        @Test
        @DisplayName("채팅방 메시지 수 증가 중 예외가 발생하면 멤버십 점수 갱신을 수행하지 않고 예외를 전파한다")
        void handle_shouldStopWhenIncrementMessageCountFails() {
            // given
            RuntimeException exception =
                    new RuntimeException("increment message count failed");

            ChatMessage message = chatMessage();
            ChatMessagePayload payload = ChatMessagePayloadMapper.fromDomain(message);

            ChatMessagePersistEvent event = new ChatMessagePersistEvent(
                    payload,
                    memberIds
            );

            doThrow(exception)
                    .when(chatRoomPersistencePort)
                    .incrementMessageCount(roomId, 1);

            // when & then
            assertThatThrownBy(() -> sut.handle(event, txId))
                    .isSameAs(exception);

            InOrder inOrder = inOrder(
                    chatMessagePersistencePort,
                    chatRoomPersistencePort
            );

            inOrder.verify(chatMessagePersistencePort)
                    .save(any(ChatMessage.class));

            inOrder.verify(chatRoomPersistencePort)
                    .incrementMessageCount(roomId, 1);

            then(chatRoomPersistencePort)
                    .should(never())
                    .updateMembershipScores(any(), any(), anyLong());
        }

        @Test
        @DisplayName("이미 처리된 메시지면 채팅방 갱신 작업을 수행하지 않고 정상 종료한다")
        void handle_shouldReturn_whenDuplicateChatMessageExceptionOccurs() {
            // given
            ChatMessage message = chatMessage();
            ChatMessagePayload payload = ChatMessagePayloadMapper.fromDomain(message);

            ChatMessagePersistEvent event = new ChatMessagePersistEvent(
                    payload,
                    memberIds
            );

            DuplicateChatMessageException exception =
                    new DuplicateChatMessageException(
                            "duplicate chat message. messageId=" + messageId,
                            new RuntimeException("duplicate key")
                    );

            doThrow(exception)
                    .when(chatMessagePersistencePort)
                    .save(any(ChatMessage.class));

            // when
            sut.handle(event, txId);

            // then
            then(chatMessagePersistencePort)
                    .should()
                    .save(any(ChatMessage.class));

            then(chatRoomPersistencePort)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handleBatch ChatMessagePersistEvent")
    class HandleBatchChatMessagePersistEventTest {

        @Test
        @DisplayName("신규 메시지만 방 카운터와 멤버십 갱신에 반영한다")
        void handleBatch_shouldApplyRoomUpdatesOnlyToInsertedMessages() {
            // given
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(metrics).recordMessageInsert(any(Runnable.class));
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(metrics).recordRoomCounter(any(Runnable.class));
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(metrics).recordMembership(any(Runnable.class));

            ChatMessage secondMessage = ChatMessage.rehydrate(
                    "100000000000000000000002",
                    roomId,
                    writerId,
                    "second",
                    createdAt.plusSeconds(1)
            );
            ChatMessagePersistEvent firstEvent = persistEvent();
            ChatMessagePersistEvent secondEvent = new ChatMessagePersistEvent(
                    ChatMessagePayloadMapper.fromDomain(secondMessage),
                    Set.of(memberId1, memberId2)
            );
            given(chatMessagePersistencePort.saveAll(anySet()))
                    .willReturn(Set.of(secondMessage.getId()));

            // when
            sut.handleBatch(List.of(firstEvent, secondEvent), txId);

            // then
            then(chatMessagePersistencePort).should().saveAll(anySet());
            then(chatRoomPersistencePort).should().incrementMessageCount(roomId, 1);
            then(chatRoomPersistencePort).should().updateMembershipScores(
                    roomId,
                    Set.of(memberId1, memberId2),
                    secondMessage.createdAtEpochMillis()
            );
            then(metrics).should().recordDuplicateMessage();
        }
    }

    @Nested
    @DisplayName("recover ChatMessagePersistEvent")
    class RecoverChatMessagePersistEventTest {

        @Test
        @DisplayName("재시도 소진 뒤 DLQ 발행에 성공하면 published 전이를 기록한다")
        void recover_shouldRecordPublished_whenDlqPublishSucceeds() {
            // given
            ChatMessagePersistEvent event = persistEvent();
            TemporaryChatPersistenceException exception = temporaryPersistenceException();

            // when
            sut.recover(exception, event, txId);

            // then
            then(dlqEventListPublishPort)
                    .should()
                    .publish(any(ChatMessageDlqEventList.class));
            then(metrics).should().recordDlqPublished();
            then(metrics).should(never()).recordDlqPublishFailed();
        }

        @Test
        @DisplayName("DLQ 발행도 실패하면 예외를 삼키고 publish_failed 전이를 기록한다")
        void recover_shouldRecordPublishFailed_whenDlqPublishFails() {
            // given
            ChatMessagePersistEvent event = persistEvent();
            TemporaryChatPersistenceException exception = temporaryPersistenceException();

            doThrow(new RuntimeException("dlq publish failed"))
                    .when(dlqEventListPublishPort)
                    .publish(any(ChatMessageDlqEventList.class));

            // when
            sut.recover(exception, event, txId);

            // then
            then(metrics).should().recordDlqPublishFailed();
            then(metrics).should(never()).recordDlqPublished();
        }
    }

    private ChatMessage chatMessage() {
        return ChatMessage.rehydrate(
                messageId,
                roomId,
                writerId,
                content,
                createdAt
        );
    }

    private ChatMessagePersistEvent persistEvent() {
        ChatMessagePayload payload = ChatMessagePayloadMapper.fromDomain(chatMessage());
        return new ChatMessagePersistEvent(payload, memberIds);
    }

    private TemporaryChatPersistenceException temporaryPersistenceException() {
        return new TemporaryChatPersistenceException(
                "temporary message save failure",
                new RuntimeException("mongo unavailable")
        );
    }

}
