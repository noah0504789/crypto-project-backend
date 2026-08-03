package org.example.chat.chatroom.adapter.in.stream;

import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.port.in.ChatRoomDlqHandler;
import org.example.chat.chatroom.application.port.in.ChatRoomEventHandler;
import org.example.common.dlq.application.service.DlqService;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.event.HandleableEvent;
import org.example.common.event.RecoverableEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class KafkaChatRoomBinder {

    @Bean
    public Consumer<Message<HandleableEvent<ChatRoomEventHandler>>> chatRoomEventConsumer(ChatRoomEventHandler handler) {
        return message -> message.getPayload().handle(handler, message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value())+"");
    }

    @Bean
    public Consumer<Message<RecoverableEvent<ChatRoomDlqHandler>>> chatRoomDlqEventConsumer(ChatRoomDlqHandler handler, DlqService dlqService) {
        return message -> {
            MessageHeaders headers = message.getHeaders();
            String dlqId = headers.get(KafkaHeaderKey.DLQ_ID.value())+"";
            String txId = headers.get(KafkaHeaderKey.TRANSACTION_ID.value())+"";

            RecoverableEvent<ChatRoomDlqHandler> event = message.getPayload();

            try {
                event.handle(handler);
            } catch (RuntimeException e) {
                dlqService.fail(dlqId, e.getMessage());

                log.error("[dlq] handling failed. dlqId={}, txId={}", dlqId, txId, e);
                return;
            }

            dlqService.complete(dlqId);

            log.debug("[dlq] mark completed. dlqId={}, txId={}", dlqId, txId);
        };
    }
}
