package org.example.notification.domain.port;

import org.example.notification.domain.event.NotificationSaveEvent;

public interface NotificationEventHandler {

    void handle(NotificationSaveEvent event, String txId);
}
