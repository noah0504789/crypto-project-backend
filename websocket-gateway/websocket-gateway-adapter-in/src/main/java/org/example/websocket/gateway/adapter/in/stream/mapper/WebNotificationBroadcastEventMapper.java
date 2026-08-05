package org.example.websocket.gateway.adapter.in.stream.mapper;

import org.example.notification.contract.event.WebNotificationBroadcastEvent;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;
import org.springframework.stereotype.Component;

@Component
public class WebNotificationBroadcastEventMapper {

    public WebNotificationCommand toCommand(WebNotificationBroadcastEvent event) {
        WebNotificationPayload payload = event.getPayload();

        return new WebNotificationCommand(
                event.getPartitionKey(),
                event.getNotificationId(),
                payload.type(),
                payload.title(),
                payload.body(),
                payload.createdAtMs(),
                payload.link(),
                payload.typedPayload().toMap()
        );
    }
}
