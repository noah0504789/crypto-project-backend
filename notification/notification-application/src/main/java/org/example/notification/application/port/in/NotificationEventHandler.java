package org.example.notification.application.port.in;

import org.example.notification.application.event.NotificationSaveEvent;

public interface NotificationEventHandler {

    void handle(NotificationSaveEvent event, String txId);
}
