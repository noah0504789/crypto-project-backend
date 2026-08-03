package org.example.chat.chatmessage.adapter.in.stream;

import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.port.in.ChatMessageDlqHandler;
import org.example.chat.chatmessage.application.port.in.ChatMessageEventHandler;
import org.example.common.event.HandleableEvent;
import org.example.common.event.RecoverableEvent;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.dlq.application.service.DlqService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class KafkaChatMessageBinder {

    @Bean
    public Consumer<Message<HandleableEvent<ChatMessageEventHandler>>> chatMessageEventConsumer(ChatMessageEventHandler handler) {
        return message -> message.getPayload().handle(handler, message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value())+"");
    }

    @Bean
    public Consumer<Message<RecoverableEvent<ChatMessageDlqHandler>>> chatMessageDlqEventConsumer(ChatMessageDlqHandler handler, DlqService dlqService) {
        return message -> {
            MessageHeaders headers = message.getHeaders();
            String dlqId = headers.get(KafkaHeaderKey.DLQ_ID.value())+"";
            String txId = headers.get(KafkaHeaderKey.TRANSACTION_ID.value())+"";

            RecoverableEvent<ChatMessageDlqHandler> event = message.getPayload();

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
