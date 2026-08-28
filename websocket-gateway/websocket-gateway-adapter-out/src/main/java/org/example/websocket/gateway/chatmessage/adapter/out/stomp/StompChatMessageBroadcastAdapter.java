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
import java.util.Set;

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

    /**
     * 배칭 어댑터가 적재 시점에 같은 판정을 재사용한다. 창이 닫힌 뒤에는 멤버 정보가 없어
     * 이 검사를 다시 할 수 없으므로, 걸러내는 위치는 적재 시점이어야 한다.
     */
    public boolean hasAnyLocalMember(Set<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return false;
        }

        return memberIds.stream().anyMatch(localSessionCache::hasUser);
    }

    /**
     * 같은 방의 메시지 여러 건을 한 프레임으로 보낸다. 로컬 멤버 판정은 적재 시점에 이미 끝났다고 본다.
     */
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
            // 배칭이 꺼진 경로에서도 프론트가 한 가지 모양만 다루도록 1건짜리 봉투로 보낸다.
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
