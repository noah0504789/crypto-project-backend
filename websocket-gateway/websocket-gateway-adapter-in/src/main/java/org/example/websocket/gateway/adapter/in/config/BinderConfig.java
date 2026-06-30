package org.example.websocket.gateway.adapter.in.config;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.KafkaHeaderKey;
import org.example.contract.chatmessage.ChatMessageBroadcastEvent;
import org.example.contract.chatroom.MyChatRoomBadgeEvent;
import org.example.notification.contract.event.WebNotificationEvent;
import org.example.websocket.gateway.adapter.in.event.chatmessage.ChatMessageBroadcastEventMapper;
import org.example.websocket.gateway.adapter.in.event.chatroom.MyChatRoomBadgeEventMapper;
import org.example.websocket.gateway.adapter.in.event.notification.WebNotificationEventMapper;
import org.example.websocket.gateway.chatmessage.application.port.in.ChatMessageBroadcastEventHandler;
import org.example.websocket.gateway.chatroom.application.port.in.MyChatRoomBadgeEventHandler;
import org.example.websocket.gateway.notification.application.port.in.WebNotificationEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class BinderConfig {

    @Bean
    public Consumer<Message<MyChatRoomBadgeEvent>> chatRoomBroadcastEventConsumer(MyChatRoomBadgeEventHandler handler, MyChatRoomBadgeEventMapper mapper) {
        return message -> handler.handle(
                mapper.toCommand(message.getPayload()),
                message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + ""
        );
    }

    @Bean
    public Consumer<Message<ChatMessageBroadcastEvent>> chatMessageBroadcastEventConsumer(ChatMessageBroadcastEventHandler handler, ChatMessageBroadcastEventMapper mapper) {
        return message -> handler.handle(
                mapper.toCommand(message.getPayload()),
                message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + ""
        );
    }

    @Bean
    public Consumer<Message<WebNotificationEvent>> webNotificationEventConsumer(WebNotificationEventHandler handler, WebNotificationEventMapper mapper) {
        return message -> handler.handle(
                mapper.toCommand(message.getPayload()),
                message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + ""
        );
    }
}
