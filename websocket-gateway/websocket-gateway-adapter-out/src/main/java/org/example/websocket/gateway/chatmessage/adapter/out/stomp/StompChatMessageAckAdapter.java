package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import lombok.RequiredArgsConstructor;
import org.example.common.enums.StompDestination;
import org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload.StompChatMessageAckPayload;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageAckResult;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageAckPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompChatMessageAckAdapter implements ChatMessageAckPort {

    private final SimpMessagingTemplate stompTemplate;

    @Override
    public void success(String userId, ChatMessageAckResult result) {
        stompTemplate.convertAndSendToUser(
                userId,
                StompDestination.CHAT_ACK_QUEUE.destination(),
                StompChatMessageAckPayload.ofSuccess(
                        result.messageId(),
                        result.clientMessageId(),
                        result.success(),
                        result.timestamp()
                )
        );
    }

    @Override
    public void failure(String userId, String clientMessageId, String errorCode) {
        stompTemplate.convertAndSendToUser(
                userId,
                StompDestination.CHAT_ACK_QUEUE.destination(),
                StompChatMessageAckPayload.ofFailure(clientMessageId, errorCode)
        );
    }
}