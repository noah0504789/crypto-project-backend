package org.example.chat.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.service.ChatMessageDlqService;
import org.example.chat.chatmessage.application.service.ChatMessageEventService;
import org.example.chat.chatroom.application.service.ChatRoomDlqService;
import org.example.chat.chatroom.application.service.ChatRoomEventService;
import org.example.common.event.HandleableEvent;
import org.example.common.event.RecoverableEvent;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.dlq.application.DlqService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class BinderConfig {

    @Bean
    public Consumer<Message<HandleableEvent<ChatRoomEventService>>> chatRoomEventConsumer(ChatRoomEventService handler) {
        return message -> message.getPayload().handle(handler, message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value())+"");
    }

    @Bean
    public Consumer<Message<HandleableEvent<ChatMessageEventService>>> chatMessageEventConsumer(ChatMessageEventService handler) {
        return message -> message.getPayload().handle(handler, message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value())+"");
    }

    @Bean
    public Consumer<Message<RecoverableEvent<ChatRoomDlqService>>> chatRoomDlqEventConsumer(ChatRoomDlqService handler, DlqService dlqService) {
        return message -> {
            MessageHeaders headers = message.getHeaders();
            String dlqId = headers.get(KafkaHeaderKey.DLQ_ID.value())+"";
            String txId = headers.get(KafkaHeaderKey.TRANSACTION_ID.value())+"";

            RecoverableEvent<ChatRoomDlqService> event = message.getPayload();

            try {
                event.handle(handler);
            } catch (RuntimeException e) {
                dlqService.fail(dlqId, e.getMessage());

                log.error("❌ dlq 처리 실패: dlqId={}, txId={}, error={}", dlqId, txId, e.getMessage(), e);
                return;
            }

            dlqService.complete(dlqId);

            log.debug("✅ dlq 완료 처리 성공: dlqId={}, txId={}", dlqId, txId);
        };
    }

    @Bean
    public Consumer<Message<RecoverableEvent<ChatMessageDlqService>>> chatMessageDlqEventConsumer(ChatMessageDlqService handler, DlqService dlqService) {
        return message -> {
            MessageHeaders headers = message.getHeaders();
            String dlqId = headers.get(KafkaHeaderKey.DLQ_ID.value())+"";
            String txId = headers.get(KafkaHeaderKey.TRANSACTION_ID.value())+"";

            RecoverableEvent<ChatMessageDlqService> event = message.getPayload();

            try {
                event.handle(handler);
            } catch (RuntimeException e) {
                dlqService.fail(dlqId, e.getMessage());

                log.error("❌ dlq 처리 실패: dlqId={}, txId={}, error={}", dlqId, txId, e.getMessage(), e);
                return;
            }

            dlqService.complete(dlqId);

            log.debug("✅ dlq 완료 처리 성공: dlqId={}, txId={}", dlqId, txId);
        };
    }
}
