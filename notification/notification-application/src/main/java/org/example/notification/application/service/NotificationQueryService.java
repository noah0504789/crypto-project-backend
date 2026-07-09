package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import org.example.notification.application.port.in.NotificationQueryUseCase;
import org.example.notification.application.port.out.NotificationPersistencePort;
import org.example.notification.application.service.query.ListNotificationInboxItemsQuery;
import org.example.notification.application.service.result.NotificationInboxItem;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationQueryService implements NotificationQueryUseCase {

    private final NotificationPersistencePort notificationPersistencePort;

    @Override
    public List<NotificationInboxItem> listInboxItems(ListNotificationInboxItemsQuery query) {
        int limit = query.limit();

        return query.hasNoCursor() ?
                notificationPersistencePort.listLatestInboxItems(query.receiverId(), limit) :
                notificationPersistencePort.listInboxItemsBefore(query.receiverId(), query.lastRecipientId(), query.cursorDeliveredAtMs(), limit);
    }
}