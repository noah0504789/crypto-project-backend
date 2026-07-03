package org.example.notification.application.port.in;

import org.example.notification.application.service.query.ListNotificationInboxItemsQuery;
import org.example.notification.application.service.result.NotificationInboxItem;

import java.util.List;
import java.util.UUID;

public interface NotificationQueryUseCase {

    List<NotificationInboxItem> listInboxItems(ListNotificationInboxItemsQuery query);
}