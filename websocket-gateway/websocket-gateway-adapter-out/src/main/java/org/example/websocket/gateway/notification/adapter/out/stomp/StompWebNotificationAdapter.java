package org.example.websocket.gateway.notification.adapter.out.stomp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.StompDestination;
import org.example.websocket.gateway.notification.adapter.out.stomp.payload.StompWebNotificationPayload;
import org.example.websocket.gateway.notification.application.port.out.WebNotificationPort;
import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompWebNotificationAdapter implements WebNotificationPort {

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    private final SimpMessagingTemplate stompTemplate;
    private final LocalSessionCache localSessionCache;

    @Override
    public boolean send(WebNotificationCommand command, String txId) {
        String receiverId = command.receiverId();

        if (!localSessionCache.hasUser(receiverId)) {
            log.debug(
                    "STOMP notification skip. no local session. txId={}, receiverId={}, serverId={}",
                    txId,
                    receiverId,
                    instanceId
            );
            return false;
        }

        return sendToUser(receiverId, StompDestination.NOTIFICATION_PREFIX.destination(), StompWebNotificationPayload.from(command), txId);
    }

    private boolean sendToUser(
            String receiverId,
            String destination,
            StompWebNotificationPayload payload,
            String txId
    ) {
        try {
            stompTemplate.convertAndSendToUser(receiverId, destination, payload);

            log.debug(
                    "STOMP notification sent. txId={}, receiverId={}, destination={}, serverId={}",
                    txId,
                    receiverId,
                    destination,
                    instanceId
            );

            return true;
        } catch (Exception e) {
            log.error(
                    "❌ STOMP notification failed. txId={}, receiverId={}, destination={}, serverId={}, error={}",
                    txId,
                    receiverId,
                    destination,
                    instanceId,
                    e.getMessage(),
                    e
            );

            return false;
        }
    }
}