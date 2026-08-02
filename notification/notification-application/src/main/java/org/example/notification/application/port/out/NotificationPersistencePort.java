package org.example.notification.application.port.out;

import org.example.notification.application.service.result.NotificationInboxItem;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationRecipient;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface NotificationPersistencePort {

    List<NotificationInboxItem> listLatestInboxItems(UUID receiverId, int limit);

    List<NotificationInboxItem> listInboxItemsBefore(
            UUID receiverId,
            String lastRecipientId,
            Long lastDeliveredAtMs,
            int limit
    );

    List<NotificationRecipient> listLatestRecipients(UUID receiverId, int limit);

    List<NotificationRecipient> listRecipientsBefore(
            UUID receiverId,
            String lastRecipientId,
            Long lastDeliveredAtMs,
            int limit
    );

    List<Notification> findMastersByIds(Set<String> ids);

    Notification save(Notification notification);

    void saveRecipients(List<NotificationRecipient> recipients);

    boolean markAsRead(String notificationId, UUID receiverId);
}