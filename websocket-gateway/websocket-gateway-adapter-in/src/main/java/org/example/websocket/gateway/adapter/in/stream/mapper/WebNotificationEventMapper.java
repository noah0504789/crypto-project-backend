package org.example.websocket.gateway.adapter.in.stream.mapper;

import org.example.notification.contract.event.WebNotificationEvent;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;
import org.springframework.stereotype.Component;

@Component
public class WebNotificationEventMapper {

    public WebNotificationCommand toCommand(WebNotificationEvent event) {
        WebNotificationPayload payload = event.getPayload();

        return new WebNotificationCommand(
                event.getPartitionKey(),
                payload.type(),
                payload.title(),
                payload.body(),
                payload.createdAtMs(),
                payload.link(),
                payload.typedPayload().toMap()
        );
    }
}