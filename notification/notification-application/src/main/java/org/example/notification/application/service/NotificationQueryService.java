package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import org.example.notification.application.port.in.NotificationQueryUseCase;
import org.example.notification.application.port.out.NotificationPersistencePort;
import org.example.notification.application.service.result.NotificationInboxItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryService implements NotificationQueryUseCase {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final NotificationPersistencePort notificationPersistencePort;

    @Override
    @Transactional(transactionManager = "notificationMongoTransactionManager", readOnly = true)
    public List<NotificationInboxItem> listLatest(UUID receiverId, int limit) {
        return notificationPersistencePort.listLatest(receiverId, normalizeLimit(limit));
    }

    @Override
    @Transactional(transactionManager = "notificationMongoTransactionManager", readOnly = true)
    public List<NotificationInboxItem> listPrev(
            UUID receiverId,
            String lastRecipientId,
            Long lastDeliveredAtMillis,
            int limit
    ) {
        if (lastRecipientId == null || lastRecipientId.isBlank() || lastDeliveredAtMillis == null) {
            return listLatest(receiverId, limit);
        }

        return notificationPersistencePort.listPrev(
                receiverId,
                lastRecipientId,
                lastDeliveredAtMillis,
                normalizeLimit(limit)
        );
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}