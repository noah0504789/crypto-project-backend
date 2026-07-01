package org.example.notification.application.port.in;

import org.example.notification.application.service.result.NotificationInboxItem;

import java.util.List;
import java.util.UUID;

public interface NotificationQueryUseCase {

    List<NotificationInboxItem> listLatest(UUID receiverId, int limit);

    List<NotificationInboxItem> listPrev(
            UUID receiverId,
            String lastRecipientId,
            Long lastDeliveredAtMillis,
            int limit
    );
}