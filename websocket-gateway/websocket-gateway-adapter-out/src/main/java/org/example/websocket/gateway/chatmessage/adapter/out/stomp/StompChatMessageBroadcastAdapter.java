package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.StompDestination;
import org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload.StompChatMessagePayload;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageBroadcastPort;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompChatMessageBroadcastAdapter implements ChatMessageBroadcastPort {

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    private final SimpMessagingTemplate stompTemplate;
    private final LocalSessionCache localSessionCache;

    @Override
    public boolean broadcast(ChatMessageBroadcastCommand command, String txId) {
        if (!hasAnyLocalMember(command.memberIds())) {
            log.debug(
                    "[stomp] skip. no local member session. txId={}, roomId={}, serverId={}",
                    txId,
                    command.roomId(),
                    instanceId
            );
            return false;
        }

        return sendChatMessage(command, txId);
    }

    private boolean hasAnyLocalMember(Set<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return false;
        }

        return memberIds.stream().anyMatch(localSessionCache::hasUser);
    }

    private boolean sendChatMessage(ChatMessageBroadcastCommand command, String txId) {
        String destination = StompDestination.CHAT_ROOM_PREFIX.destination(command.roomId());
        StompChatMessagePayload payload = StompChatMessagePayload.from(command);

        try {
            stompTemplate.convertAndSend(destination, payload);

            log.debug(
                    "[stomp] chat body sent. txId={}, roomId={}, messageId={}, destination={}, serverId={}",
                    txId,
                    command.roomId(),
                    command.messageId(),
                    destination,
                    instanceId
            );

            return true;
        } catch (Exception e) {
            log.error(
                    "[stomp] broadcast failed. txId={}, roomId={}, destination={}, serverId={}",
                    txId,
                    command.roomId(),
                    destination,
                    instanceId,
                    e
            );

            return false;
        }
    }
}