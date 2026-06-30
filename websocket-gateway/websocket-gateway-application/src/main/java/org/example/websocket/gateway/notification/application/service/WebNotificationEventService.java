package org.example.websocket.gateway.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.websocket.gateway.notification.application.port.in.WebNotificationEventHandler;
import org.example.websocket.gateway.notification.application.port.out.WebNotificationPort;
import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebNotificationEventService implements WebNotificationEventHandler {

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    private final WebNotificationPort webNotificationPort;

    @Override
    public void handle(WebNotificationCommand command, String txId) {
        boolean sent = webNotificationPort.send(command, txId);

        if (sent) {
            log.debug(
                    "✅ STOMP notification sent. txId={}, receiverId={}, serverId={}",
                    txId,
                    command.receiverId(),
                    instanceId
            );
            return;
        }

        log.debug(
                "STOMP notification skip. no local session. txId={}, receiverId={}, serverId={}",
                txId,
                command.receiverId(),
                instanceId
        );
    }
}