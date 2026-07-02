package org.example.chat.chatmessage.adapter.in.stream;

import org.example.chat.chatmessage.domain.event.handler.ChatMessageDlqHandler;
import org.example.chat.chatmessage.domain.event.handler.ChatMessageEventHandler;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.event.HandleableEvent;
import org.example.common.event.RecoverableEvent;
import org.example.common.dlq.application.service.DlqService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.function.Consumer;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaChatMessageBinderTest {

    @Mock
    private ChatMessageEventHandler chatMessageEventHandler;

    @Mock
    private ChatMessageDlqHandler chatMessageDlqHandler;

    @Mock
    private DlqService dlqService;

    @InjectMocks
    private KafkaChatMessageBinder sut;

    private final String txId = "tx-1";
    private final String dlqId = "dlq-1";

    @Nested
    @DisplayName("chatMessageEventConsumer")
    class ChatMessageEventConsumerTest {

        @Test
        @DisplayName("payload handle에 ChatMessageEventHandler와 transaction_id를 전달한다")
        void should_handle_chat_message_event() {
            // given
            @SuppressWarnings("unchecked")
            HandleableEvent<ChatMessageEventHandler> event = mock(HandleableEvent.class);

            Consumer<Message<HandleableEvent<ChatMessageEventHandler>>> consumer =
                    sut.chatMessageEventConsumer(chatMessageEventHandler);

            Message<HandleableEvent<ChatMessageEventHandler>> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaderKey.TRANSACTION_ID.value(), txId)
                    .build();

            // when
            consumer.accept(message);

            // then
            verify(event).handle(chatMessageEventHandler, txId);
        }
    }

    @Nested
    @DisplayName("chatMessageDlqEventConsumer")
    class ChatMessageDlqEventConsumerTest {

        @Test
        @DisplayName("DLQ 이벤트 처리 성공 시 complete를 호출한다")
        void should_complete_when_dlq_event_handle_success() {
            // given
            @SuppressWarnings("unchecked")
            RecoverableEvent<ChatMessageDlqHandler> event = mock(RecoverableEvent.class);

            Consumer<Message<RecoverableEvent<ChatMessageDlqHandler>>> consumer =
                    sut.chatMessageDlqEventConsumer(chatMessageDlqHandler, dlqService);

            Message<RecoverableEvent<ChatMessageDlqHandler>> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaderKey.DLQ_ID.value(), dlqId)
                    .setHeader(KafkaHeaderKey.TRANSACTION_ID.value(), txId)
                    .build();

            // when
            consumer.accept(message);

            // then
            InOrder inOrder = inOrder(event, dlqService);

            inOrder.verify(event).handle(chatMessageDlqHandler);
            inOrder.verify(dlqService).complete(dlqId);

            verify(dlqService, never()).fail(anyString(), anyString());
        }

        @Test
        @DisplayName("DLQ 이벤트 처리 실패 시 fail을 호출하고 complete를 호출하지 않는다")
        void should_fail_when_dlq_event_handle_fails() {
            // given
            @SuppressWarnings("unchecked")
            RecoverableEvent<ChatMessageDlqHandler> event = mock(RecoverableEvent.class);

            RuntimeException exception = new RuntimeException("recover failed");

            doThrow(exception)
                    .when(event)
                    .handle(chatMessageDlqHandler);

            Consumer<Message<RecoverableEvent<ChatMessageDlqHandler>>> consumer =
                    sut.chatMessageDlqEventConsumer(chatMessageDlqHandler, dlqService);

            Message<RecoverableEvent<ChatMessageDlqHandler>> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaderKey.DLQ_ID.value(), dlqId)
                    .setHeader(KafkaHeaderKey.TRANSACTION_ID.value(), txId)
                    .build();

            // when
            consumer.accept(message);

            // then
            verify(event).handle(chatMessageDlqHandler);
            verify(dlqService).fail(dlqId, "recover failed");
            verify(dlqService, never()).complete(anyString());
        }
    }
}