package org.example.websocket.gateway.notification.application.port.in;

import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;

public interface WebNotificationSendUseCase {

    void send(WebNotificationCommand command, String txId);
}