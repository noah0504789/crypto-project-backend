package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import org.example.notification.application.port.in.NotificationCommandUseCase;
import org.example.notification.application.port.out.NotificationPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationCommandService implements NotificationCommandUseCase {

    private final NotificationPersistencePort notificationPersistencePort;

    @Override
    @Transactional("notificationMongoTransactionManager")
    public boolean markAsRead(String id, UUID receiverId) {
        if (id == null || id.isBlank()) {
            return false;
        }

        return notificationPersistencePort.markAsRead(id, receiverId);
    }
}