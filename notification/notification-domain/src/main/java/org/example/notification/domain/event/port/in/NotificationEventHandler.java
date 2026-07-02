package org.example.notification.domain.event.port.in;

import org.example.notification.domain.event.NotificationSaveEvent;

public interface NotificationEventHandler {

    void handle(NotificationSaveEvent event, String txId);
}
