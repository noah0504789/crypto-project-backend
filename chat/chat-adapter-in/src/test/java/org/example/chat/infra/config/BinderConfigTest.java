package org.example.chat.infra.config;

import org.example.chat.chatmessage.application.service.ChatMessageDlqService;
import org.example.chat.chatmessage.application.service.ChatMessageEventService;
import org.example.chat.chatroom.application.service.ChatRoomDlqService;
import org.example.chat.chatroom.application.service.ChatRoomEventService;
import org.example.common.event.HandleableEvent;
import org.example.common.event.RecoverableEvent;
import org.example.common.dlq.application.service.DlqService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.function.Consumer;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BinderConfigTest {

    @Mock
    private ChatRoomEventService chatRoomEventService;

    @Mock
    private ChatMessageEventService chatMessageEventService;

    @Mock
    private ChatRoomDlqService chatRoomDlqService;

    @Mock
    private ChatMessageDlqService chatMessageDlqService;

    @Mock
    private DlqService dlqService;

    @InjectMocks
    private BinderConfig sut;

    private final String txId = "tx-1";
    private final String dlqId = "dlq-1";

    @Test
    @DisplayName("chatRoomEventConsumer는 payload handle에 ChatRoomEventService와 transaction_id를 전달한다")
    void chatRoomEventConsumer() {
        // given
        @SuppressWarnings("unchecked")
        HandleableEvent<ChatRoomEventService> event = mock(HandleableEvent.class);

        Consumer<Message<HandleableEvent<ChatRoomEventService>>> consumer =
                sut.chatRoomEventConsumer(chatRoomEventService);

        Message<HandleableEvent<ChatRoomEventService>> message = MessageBuilder
                .withPayload(event)
                .setHeader("transaction_id", txId)
                .build();

        // when
        consumer.accept(message);

        // then
        verify(event).handle(chatRoomEventService, txId);
    }

    @Test
    @DisplayName("chatMessageEventConsumer는 payload handle에 ChatMessageEventService와 transaction_id를 전달한다")
    void chatMessageEventConsumer() {
        // given
        @SuppressWarnings("unchecked")
        HandleableEvent<ChatMessageEventService> event = mock(HandleableEvent.class);

        Consumer<Message<HandleableEvent<ChatMessageEventService>>> consumer =
                sut.chatMessageEventConsumer(chatMessageEventService);

        Message<HandleableEvent<ChatMessageEventService>> message = MessageBuilder
                .withPayload(event)
                .setHeader("transaction_id", txId)
                .build();

        // when
        consumer.accept(message);

        // then
        verify(event).handle(chatMessageEventService, txId);
    }

    @Test
    @DisplayName("chatRoomDlqEventConsumer는 DLQ 이벤트 처리 성공 시 complete를 호출한다")
    void chatRoomDlqEventConsumerSuccess() {
        // given
        @SuppressWarnings("unchecked")
        RecoverableEvent<ChatRoomDlqService> event = mock(RecoverableEvent.class);

        Consumer<Message<RecoverableEvent<ChatRoomDlqService>>> consumer =
                sut.chatRoomDlqEventConsumer(chatRoomDlqService, dlqService);

        Message<RecoverableEvent<ChatRoomDlqService>> message = MessageBuilder
                .withPayload(event)
                .setHeader("dlq_id", dlqId)
                .setHeader("transaction_id", txId)
                .build();

        // when
        consumer.accept(message);

        // then
        InOrder inOrder = inOrder(event, dlqService);

        inOrder.verify(event).handle(chatRoomDlqService);
        inOrder.verify(dlqService).complete(dlqId);

        verify(dlqService, never()).fail(anyString(), anyString());
    }

    @Test
    @DisplayName("chatRoomDlqEventConsumer는 DLQ 이벤트 처리 실패 시 fail을 호출하고 complete를 호출하지 않는다")
    void chatRoomDlqEventConsumerFail() {
        // given
        @SuppressWarnings("unchecked")
        RecoverableEvent<ChatRoomDlqService> event = mock(RecoverableEvent.class);

        RuntimeException exception = new RuntimeException("recover failed");

        doThrow(exception)
                .when(event)
                .handle(chatRoomDlqService);

        Consumer<Message<RecoverableEvent<ChatRoomDlqService>>> consumer =
                sut.chatRoomDlqEventConsumer(chatRoomDlqService, dlqService);

        Message<RecoverableEvent<ChatRoomDlqService>> message = MessageBuilder
                .withPayload(event)
                .setHeader("dlq_id", dlqId)
                .setHeader("transaction_id", txId)
                .build();

        // when
        consumer.accept(message);

        // then
        verify(event).handle(chatRoomDlqService);
        verify(dlqService).fail(dlqId, "recover failed");
        verify(dlqService, never()).complete(anyString());
    }

    @Test
    @DisplayName("chatMessageDlqEventConsumer는 DLQ 이벤트 처리 성공 시 complete를 호출한다")
    void chatMessageDlqEventConsumerSuccess() {
        // given
        @SuppressWarnings("unchecked")
        RecoverableEvent<ChatMessageDlqService> event = mock(RecoverableEvent.class);

        Consumer<Message<RecoverableEvent<ChatMessageDlqService>>> consumer =
                sut.chatMessageDlqEventConsumer(chatMessageDlqService, dlqService);

        Message<RecoverableEvent<ChatMessageDlqService>> message = MessageBuilder
                .withPayload(event)
                .setHeader("dlq_id", dlqId)
                .setHeader("transaction_id", txId)
                .build();

        // when
        consumer.accept(message);

        // then
        InOrder inOrder = inOrder(event, dlqService);

        inOrder.verify(event).handle(chatMessageDlqService);
        inOrder.verify(dlqService).complete(dlqId);

        verify(dlqService, never()).fail(anyString(), anyString());
    }

    @Test
    @DisplayName("chatMessageDlqEventConsumer는 DLQ 이벤트 처리 실패 시 fail을 호출하고 complete를 호출하지 않는다")
    void chatMessageDlqEventConsumerFail() {
        // given
        @SuppressWarnings("unchecked")
        RecoverableEvent<ChatMessageDlqService> event = mock(RecoverableEvent.class);

        RuntimeException exception = new RuntimeException("recover failed");

        doThrow(exception)
                .when(event)
                .handle(chatMessageDlqService);

        Consumer<Message<RecoverableEvent<ChatMessageDlqService>>> consumer =
                sut.chatMessageDlqEventConsumer(chatMessageDlqService, dlqService);

        Message<RecoverableEvent<ChatMessageDlqService>> message = MessageBuilder
                .withPayload(event)
                .setHeader("dlq_id", dlqId)
                .setHeader("transaction_id", txId)
                .build();

        // when
        consumer.accept(message);

        // then
        verify(event).handle(chatMessageDlqService);
        verify(dlqService).fail(dlqId, "recover failed");
        verify(dlqService, never()).complete(anyString());
    }
}
