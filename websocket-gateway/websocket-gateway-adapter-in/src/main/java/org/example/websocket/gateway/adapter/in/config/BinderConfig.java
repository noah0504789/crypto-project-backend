package org.example.websocket.gateway.adapter.in.config;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.KafkaHeaderKey;
import org.example.contract.chatmessage.ChatMessageBroadcastEvent;
import org.example.contract.chatroom.MyChatRoomBadgeEvent;
import org.example.notification.contract.event.WebNotificationEvent;
import org.example.websocket.gateway.adapter.in.event.chatmessage.ChatMessageBroadcastEventHandler;
import org.example.websocket.gateway.adapter.in.event.chatroom.MyChatRoomBadgeEventHandler;
import org.example.websocket.gateway.adapter.in.event.notification.WebNotificationEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class BinderConfig {

    @Bean
    public Consumer<Message<MyChatRoomBadgeEvent>> chatRoomBroadcastEventConsumer(MyChatRoomBadgeEventHandler handler) {
        return message -> handler.handle(message.getPayload(), message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + "");
    }

    @Bean
    public Consumer<Message<ChatMessageBroadcastEvent>> chatMessageBroadcastEventConsumer(ChatMessageBroadcastEventHandler handler) {
        return message -> handler.handle(message.getPayload(), message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + "");
    }

    @Bean
    public Consumer<Message<WebNotificationEvent>> webNotificationEventConsumer(WebNotificationEventHandler handler) {
        return message -> handler.handle(message.getPayload(), message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + "");
    }
}
