package org.example.notification.application.port.in;

import org.example.notification.application.service.query.ListNotificationInboxItemsQuery;
import org.example.notification.application.service.result.NotificationInboxItem;

import java.util.List;

public interface NotificationQueryUseCase {

    List<NotificationInboxItem> listInboxItems(ListNotificationInboxItemsQuery query);
}