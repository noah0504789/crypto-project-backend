package org.example.websocket.gateway.adapter.in.stream;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.KafkaHeaderKey;
import org.example.contract.chatmessage.ChatMessageBroadcastEvent;
import org.example.contract.chatroom.MyChatRoomBadgeBroadcastEvent;
import org.example.notification.contract.event.WebNotificationBroadcastEvent;
import org.example.websocket.gateway.adapter.in.stream.mapper.ChatMessageBroadcastEventMapper;
import org.example.websocket.gateway.adapter.in.stream.mapper.MyChatRoomBadgeBroadcastEventMapper;
import org.example.websocket.gateway.adapter.in.stream.mapper.WebNotificationBroadcastEventMapper;
import org.example.websocket.gateway.chatmessage.application.port.in.ChatMessageBroadcastUseCase;
import org.example.websocket.gateway.chatroom.application.port.in.MyChatRoomBadgeSendUseCase;
import org.example.websocket.gateway.notification.application.port.in.WebNotificationSendUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class KafkaWebsocketGatewayBinder {

    @Bean
    public Consumer<Message<ChatMessageBroadcastEvent>> chatMessageBroadcastEventConsumer(ChatMessageBroadcastUseCase chatMessageBroadcastUseCase, ChatMessageBroadcastEventMapper mapper) {
        return message -> chatMessageBroadcastUseCase.broadcast(
                mapper.toCommand(message.getPayload()),
                message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + ""
        );
    }

    @Bean
    public Consumer<Message<MyChatRoomBadgeBroadcastEvent>> myChatRoomBadgeBroadcastEventConsumer(MyChatRoomBadgeSendUseCase myChatRoomBadgeSendUseCase, MyChatRoomBadgeBroadcastEventMapper mapper) {
        return message -> myChatRoomBadgeSendUseCase.send(
                mapper.toCommand(message.getPayload()),
                message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + ""
        );
    }

    @Bean
    public Consumer<Message<WebNotificationBroadcastEvent>> webNotificationBroadcastEventConsumer(WebNotificationSendUseCase webNotificationSendUseCase, WebNotificationBroadcastEventMapper mapper) {
        return message -> webNotificationSendUseCase.send(
                mapper.toCommand(message.getPayload()),
                message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + ""
        );
    }
}
