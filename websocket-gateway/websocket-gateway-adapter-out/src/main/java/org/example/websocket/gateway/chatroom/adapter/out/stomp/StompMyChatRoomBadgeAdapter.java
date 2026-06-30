package org.example.websocket.gateway.chatroom.adapter.out.stomp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.StompDestination;
import org.example.websocket.gateway.chatroom.adapter.out.stomp.payload.StompMyChatRoomBadgePayload;
import org.example.websocket.gateway.chatroom.application.port.out.MyChatRoomBadgePort;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompMyChatRoomBadgeAdapter implements MyChatRoomBadgePort {

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    private final SimpMessagingTemplate stompTemplate;
    private final LocalSessionCache localSessionCache;

    @Override
    public boolean send(MyChatRoomBadgeCommand command, String txId) {
        String destination = StompDestination.CHAT_ROOM_BADGE_QUEUE.destination();
        StompMyChatRoomBadgePayload payload = StompMyChatRoomBadgePayload.from(command);

        boolean sentAny = false;

        for (String memberId : command.memberIds()) {
            if (!localSessionCache.hasUser(memberId)) {
                log.debug(
                        "STOMP skip. no local session. txId={}, memberId={}, serverId={}",
                        txId,
                        memberId,
                        instanceId
                );
                continue;
            }

            if (sendToUser(memberId, destination, payload, txId)) {
                sentAny = true;
            }
        }

        return sentAny;
    }

    private boolean sendToUser(
            String memberId,
            String destination,
            StompMyChatRoomBadgePayload payload,
            String txId
    ) {
        try {
            stompTemplate.convertAndSendToUser(memberId, destination, payload);

            log.debug(
                    "STOMP sent. txId={}, memberId={}, destination={}, serverId={}",
                    txId,
                    memberId,
                    destination,
                    instanceId
            );

            return true;
        } catch (Exception e) {
            log.error(
                    "❌ STOMP 실패: txId={}, memberId={}, destination={}, serverId={}, error={}",
                    txId,
                    memberId,
                    destination,
                    instanceId,
                    e.getMessage(),
                    e
            );

            return false;
        }
    }
}