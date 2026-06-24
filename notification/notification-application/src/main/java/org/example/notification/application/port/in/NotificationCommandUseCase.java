package org.example.notification.application.port.in;

import java.util.UUID;

public interface NotificationCommandUseCase {

    boolean markAsRead(String id, UUID receiverId);
}