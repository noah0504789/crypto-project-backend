package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.StompDestination;
import org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload.StompChatMessageBatchPayload;
import org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload.StompChatMessagePayload;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageBroadcastPort;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompChatMessageBroadcastAdapter implements ChatMessageBroadcastPort {

    @Value("${app.instance-id}")
    private String instanceId;

    private final SimpMessagingTemplate stompTemplate;
    private final LocalSessionCache localSessionCache;

    @Override
    public boolean broadcast(ChatMessageBroadcastCommand command, String txId) {
        if (!hasLocalSubscriber(command.roomId())) {
            log.debug(
                    "[stomp] skip. no local subscriber. txId={}, roomId={}, serverId={}",
                    txId,
                    command.roomId(),
                    instanceId
            );
            return false;
        }

        return sendChatMessage(command, txId);
    }

    public boolean hasLocalSubscriber(String roomId) {
        return roomId != null && localSessionCache.hasLocalSubscriber(roomId);
    }

    /** 로컬 구독자 판정은 적재 시점에 이미 끝났다고 본다. */
    public boolean broadcastBatch(String roomId, List<StompChatMessagePayload> messages, String txId) {
        String destination = StompDestination.CHAT_ROOM_PREFIX.destination(roomId);

        try {
            stompTemplate.convertAndSend(destination, new StompChatMessageBatchPayload(roomId, messages));

            log.debug(
                    "[stomp] chat batch sent. txId={}, roomId={}, count={}, destination={}, serverId={}",
                    txId,
                    roomId,
                    messages.size(),
                    destination,
                    instanceId
            );

            return true;
        } catch (Exception e) {
            log.error(
                    "[stomp] batch broadcast failed. txId={}, roomId={}, count={}, destination={}, serverId={}",
                    txId,
                    roomId,
                    messages.size(),
                    destination,
                    instanceId,
                    e
            );

            return false;
        }
    }

    private boolean sendChatMessage(ChatMessageBroadcastCommand command, String txId) {
        String destination = StompDestination.CHAT_ROOM_PREFIX.destination(command.roomId());
        StompChatMessagePayload payload = StompChatMessagePayload.from(command);

        try {
            // 배칭이 꺼져도 봉투로 보낸다. 설정으로 껐다 켜도 wire 형식이 안 바뀐다.
            stompTemplate.convertAndSend(destination, new StompChatMessageBatchPayload(command.roomId(), List.of(payload)));

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
