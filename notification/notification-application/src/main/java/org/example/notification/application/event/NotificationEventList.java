package org.example.notification.application.event;

import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;


public class NotificationEventList extends AbstractOutboxEventList {

    private NotificationEventList() {
        super();
    }

    public static NotificationEventList of(AbstractOutboxEvent... events) {
        return AbstractOutboxEventList.of(NotificationEventList::new, events);
    }
}
