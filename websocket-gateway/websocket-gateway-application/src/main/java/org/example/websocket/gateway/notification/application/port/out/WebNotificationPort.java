package org.example.websocket.gateway.notification.application.port.out;

import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;

public interface WebNotificationPort {

    boolean send(WebNotificationCommand command, String txId);
}