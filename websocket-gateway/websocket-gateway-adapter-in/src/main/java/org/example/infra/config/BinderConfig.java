package org.example.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.KafkaHeaderKey;
import org.example.contract.chatmessage.ChatMessageBroadcastEvent;
import org.example.contract.chatroom.MyChatRoomBadgeEvent;
import org.example.common.event.notification.WebNotificationEvent;
import org.example.event.chatmessage.ChatMessageBroadcastEventService;
import org.example.event.chatroom.MyChatRoomBadgeEventService;
import org.example.event.notification.WebNotificationEventService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class BinderConfig {

    @Bean
    public Consumer<Message<MyChatRoomBadgeEvent>> chatRoomBroadcastEventConsumer(MyChatRoomBadgeEventService handler) {
        return message -> handler.handle(message.getPayload(), message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + "");
    }

    @Bean
    public Consumer<Message<ChatMessageBroadcastEvent>> chatMessageBroadcastEventConsumer(ChatMessageBroadcastEventService handler) {
        return message -> handler.handle(message.getPayload(), message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + "");
    }

    @Bean
    public Consumer<Message<WebNotificationEvent>> webNotificationEventConsumer(WebNotificationEventService handler) {
        return message -> handler.handle(message.getPayload(), message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + "");
    }
}
