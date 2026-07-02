package org.example.chat.chatroom.adapter.in.stream;

import org.example.chat.chatroom.domain.event.handler.ChatRoomDlqHandler;
import org.example.chat.chatroom.domain.event.handler.ChatRoomEventHandler;
import org.example.common.dlq.application.service.DlqService;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.event.HandleableEvent;
import org.example.common.event.RecoverableEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.function.Consumer;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaChatRoomBinderTest {

    @Mock
    private ChatRoomEventHandler chatRoomEventHandler;

    @Mock
    private ChatRoomDlqHandler chatRoomDlqHandler;

    @Mock
    private DlqService dlqService;

    @InjectMocks
    private KafkaChatRoomBinder sut;

    private final String txId = "tx-1";
    private final String dlqId = "dlq-1";

    @Nested
    @DisplayName("chatRoomEventConsumer")
    class ChatRoomEventConsumerTest {

        @Test
        @DisplayName("payload handle에 ChatRoomEventHandler와 transaction_id를 전달한다")
        void should_handle_chat_room_event() {
            // given
            @SuppressWarnings("unchecked")
            HandleableEvent<ChatRoomEventHandler> event = mock(HandleableEvent.class);

            Consumer<Message<HandleableEvent<ChatRoomEventHandler>>> consumer =
                    sut.chatRoomEventConsumer(chatRoomEventHandler);

            Message<HandleableEvent<ChatRoomEventHandler>> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaderKey.TRANSACTION_ID.value(), txId)
                    .build();

            // when
            consumer.accept(message);

            // then
            verify(event).handle(chatRoomEventHandler, txId);
        }
    }

    @Nested
    @DisplayName("chatRoomDlqEventConsumer")
    class ChatRoomDlqEventConsumerTest {

        @Test
        @DisplayName("DLQ 이벤트 처리 성공 시 complete를 호출한다")
        void should_complete_when_dlq_event_handle_success() {
            // given
            @SuppressWarnings("unchecked")
            RecoverableEvent<ChatRoomDlqHandler> event = mock(RecoverableEvent.class);

            Consumer<Message<RecoverableEvent<ChatRoomDlqHandler>>> consumer =
                    sut.chatRoomDlqEventConsumer(chatRoomDlqHandler, dlqService);

            Message<RecoverableEvent<ChatRoomDlqHandler>> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaderKey.DLQ_ID.value(), dlqId)
                    .setHeader(KafkaHeaderKey.TRANSACTION_ID.value(), txId)
                    .build();

            // when
            consumer.accept(message);

            // then
            InOrder inOrder = inOrder(event, dlqService);

            inOrder.verify(event).handle(chatRoomDlqHandler);
            inOrder.verify(dlqService).complete(dlqId);

            verify(dlqService, never()).fail(anyString(), anyString());
        }

        @Test
        @DisplayName("DLQ 이벤트 처리 실패 시 fail을 호출하고 complete를 호출하지 않는다")
        void should_fail_when_dlq_event_handle_fails() {
            // given
            @SuppressWarnings("unchecked")
            RecoverableEvent<ChatRoomDlqHandler> event = mock(RecoverableEvent.class);

            RuntimeException exception = new RuntimeException("recover failed");

            doThrow(exception)
                    .when(event)
                    .handle(chatRoomDlqHandler);

            Consumer<Message<RecoverableEvent<ChatRoomDlqHandler>>> consumer =
                    sut.chatRoomDlqEventConsumer(chatRoomDlqHandler, dlqService);

            Message<RecoverableEvent<ChatRoomDlqHandler>> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaderKey.DLQ_ID.value(), dlqId)
                    .setHeader(KafkaHeaderKey.TRANSACTION_ID.value(), txId)
                    .build();

            // when
            consumer.accept(message);

            // then
            verify(event).handle(chatRoomDlqHandler);
            verify(dlqService).fail(dlqId, "recover failed");
            verify(dlqService, never()).complete(anyString());
        }
    }
}